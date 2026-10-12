package com.skill2job.service;

import com.skill2job.entity.Interview;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.InterviewStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.InterviewRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Interview scheduling and the application chat: who may do what, and in which application state. */
class InterviewChatServiceTest {

    final JobFixture f = new JobFixture();
    final List<String> notes = new ArrayList<>();
    final RecordingEmailService mail = new RecordingEmailService();
    final NotificationService notifier = new NotificationService(null) {
        @Override public void notify(String userId, String message, String link) { notes.add(userId + ": " + message); }
    };
    final InterviewService interviews = new InterviewService(f.interviews, f.apps, f.jobService, notifier, mail);
    final ChatService chat = new ChatService(f.chats, f.apps, f.jobService, notifier);

    private JobApplication app(Job j, String student, ApplicationStatus st) {
        JobApplication a = new JobApplication();
        a.setJobId(j.getId());
        a.setJobTitle(j.getTitle());
        a.setCompanyName(j.getCompanyName());
        a.setEmployerId(j.getEmployerId());
        a.setStudentId(student);
        a.setStudentName("Student " + student);
        a.setStudentEmail(student + "@uni.edu");
        a.setStatus(st);
        return f.apps.save(a);
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    // ------------------------------------------------------------------ interviews

    @Test
    void proposeSelectFlowNotifiesBothSides() {
        Job j = f.post("emp1");
        JobApplication a = app(j, "s1", ApplicationStatus.SHORTLISTED);
        var iv = interviews.propose(JobFixture.employer("emp1"), a.getId(), List.of("Mon 10:00", "Tue 14:00"));
        assertEquals(InterviewStatus.PROPOSED, iv.status());
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("s1:")));
        assertEquals(1, mail.count("interview-proposed"));
        var chosen = interviews.selectSlot(JobFixture.student("s1"), iv.id(), "Tue 14:00");
        assertEquals(InterviewStatus.SCHEDULED, chosen.status());
        assertEquals("Tue 14:00", chosen.selectedSlot());
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("emp1:") && n.contains("Tue 14:00")));
    }

    @Test
    void onlyTheJobOwnerCanProposeAndRejectedApplicantsGetNoInterview() {
        Job j = f.post("emp1");
        JobApplication ok = app(j, "s1", ApplicationStatus.APPLIED);
        JobApplication rejected = app(j, "s2", ApplicationStatus.REJECTED);
        assertEquals(403, status(() -> interviews.propose(JobFixture.employer("emp2"), ok.getId(), List.of("x"))));
        assertEquals(403, status(() -> interviews.propose(JobFixture.student("s1"), ok.getId(), List.of("x"))));
        assertEquals(400, status(() -> interviews.propose(JobFixture.employer("emp1"), rejected.getId(), List.of("x"))));
        assertEquals(404, status(() -> interviews.propose(JobFixture.employer("emp1"), "ghost", List.of("x"))));
        assertEquals(0, f.interviewStore.size());
    }

    @Test
    void slotSelectionRules() {
        Job j = f.post("emp1");
        JobApplication a = app(j, "s1", ApplicationStatus.SHORTLISTED);
        var iv = interviews.propose(JobFixture.employer("emp1"), a.getId(), List.of("Mon 10:00"));
        assertEquals(403, status(() -> interviews.selectSlot(JobFixture.student("s2"), iv.id(), "Mon 10:00")), "another student");
        assertEquals(400, status(() -> interviews.selectSlot(JobFixture.student("s1"), iv.id(), "Sun 03:00")), "not a proposed slot");
        interviews.selectSlot(JobFixture.student("s1"), iv.id(), "Mon 10:00");
        assertEquals(400, status(() -> interviews.selectSlot(JobFixture.student("s1"), iv.id(), "Mon 10:00")), "already scheduled");
    }

    @Test
    void cancelledInterviewCannotBePickedAndTheStudentIsTold() {
        Job j = f.post("emp1");
        JobApplication a = app(j, "s1", ApplicationStatus.SHORTLISTED);
        var iv = interviews.propose(JobFixture.employer("emp1"), a.getId(), List.of("Mon 10:00"));
        assertEquals(403, status(() -> interviews.cancel(JobFixture.employer("emp2"), iv.id())));
        interviews.cancel(JobFixture.employer("emp1"), iv.id());
        assertEquals(InterviewStatus.CANCELLED, f.interviews.findById(iv.id()).get().getStatus());
        assertEquals(400, status(() -> interviews.selectSlot(JobFixture.student("s1"), iv.id(), "Mon 10:00")));
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("s1:") && n.contains("cancelled")));
    }

    @Test
    void mineIsScopedToTheCallersRole() {
        Job j = f.post("emp1");
        interviews.propose(JobFixture.employer("emp1"), app(j, "s1", ApplicationStatus.SHORTLISTED).getId(), List.of("a"));
        interviews.propose(JobFixture.employer("emp1"), app(j, "s2", ApplicationStatus.SHORTLISTED).getId(), List.of("b"));
        assertEquals(1, interviews.mine(JobFixture.student("s1")).size());
        assertEquals(2, interviews.mine(JobFixture.employer("emp1")).size());
        assertEquals(0, interviews.mine(JobFixture.employer("emp2")).size());
    }

    // ------------------------------------------------------------------ chat

    @Test
    void chatIsClosedUntilShortlistedAndClosedForSendingAfterRejection() {
        Job j = f.post("emp1");
        JobApplication applied = app(j, "s1", ApplicationStatus.APPLIED);
        JobApplication rejected = app(j, "s2", ApplicationStatus.REJECTED);
        assertEquals(400, status(() -> chat.list(JobFixture.student("s1"), applied.getId())));
        assertEquals(400, status(() -> chat.send(JobFixture.student("s1"), applied.getId(), "hi")));
        assertEquals(400, status(() -> chat.send(JobFixture.employer("emp1"), rejected.getId(), "hi")));
        assertEquals(200, status(() -> chat.list(JobFixture.student("s2"), rejected.getId())), "history stays readable");
    }

    @Test
    void shortlistedApplicantAndOwnerCanTalkAndAreNotifiedOfEachMessage() {
        Job j = f.post("emp1");
        JobApplication a = app(j, "s1", ApplicationStatus.SHORTLISTED);
        chat.send(JobFixture.employer("emp1"), a.getId(), "  Hello  ");
        chat.send(JobFixture.student("s1"), a.getId(), "Hi!");
        var msgs = chat.list(JobFixture.student("s1"), a.getId());
        assertEquals(2, msgs.size());
        assertEquals("Hello", msgs.get(0).text(), "text is trimmed");
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("s1:")), "student notified");
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("emp1:")), "employer notified");
    }

    @Test
    void strangersCannotReadOrWriteAConversation() {
        Job j = f.post("emp1");
        JobApplication a = app(j, "s1", ApplicationStatus.SHORTLISTED);
        assertEquals(403, status(() -> chat.list(JobFixture.student("s2"), a.getId())));
        assertEquals(403, status(() -> chat.send(JobFixture.student("s2"), a.getId(), "x")));
        assertEquals(403, status(() -> chat.list(JobFixture.employer("emp2"), a.getId())));
        assertEquals(403, status(() -> chat.send(JobFixture.employer("emp2"), a.getId(), "x")));
        assertEquals(200, status(() -> chat.list(JobFixture.admin(), a.getId())));
        assertEquals(404, status(() -> chat.list(JobFixture.student("s1"), "ghost")));
    }
}

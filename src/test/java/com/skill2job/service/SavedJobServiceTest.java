package com.skill2job.service;

import com.skill2job.entity.Job;
import com.skill2job.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SavedJobServiceTest {

    final JobFixture f = new JobFixture();
    final SavedJobService svc = new SavedJobService(f.saved, f.jobService);

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void saveListAndUnsave() {
        Job j = f.post("emp1");
        svc.save("s1", j.getId());
        assertEquals(1, svc.mine("s1").size());
        assertEquals(0, svc.mine("s2").size(), "bookmarks are private to the student");
        svc.unsave("s1", j.getId());
        assertEquals(0, svc.mine("s1").size());
    }

    @Test
    void duplicateBookmarkUnknownJobAndUnsavingTheUnsavedAreRejected() {
        Job j = f.post("emp1");
        svc.save("s1", j.getId());
        assertEquals(409, status(() -> svc.save("s1", j.getId())));
        assertEquals(404, status(() -> svc.save("s1", "ghost")));
        assertEquals(404, status(() -> svc.unsave("s1", "never-saved")));
        assertEquals(1, f.savedStore.size());
    }

    /** Related to audit F-13: a student must not be able to bookmark (and so learn the title of) an unpublished job. */
    @Test
    void unpaidJobCannotBeBookmarked() {
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) f.post("emp1");
        Job unpaid = f.post("emp1");
        assertEquals(404, status(() -> svc.save("s1", unpaid.getId())));
    }

    @Test
    void deletingTheJobRemovesItsBookmarks() {
        Job j = f.post("emp1");
        svc.save("s1", j.getId());
        f.jobService.delete(JobFixture.employer("emp1"), j.getId());
        assertEquals(0, svc.mine("s1").size());
    }
}

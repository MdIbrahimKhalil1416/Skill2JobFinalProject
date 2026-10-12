package com.skill2job.gamification;

import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.XpEvent;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.XpEventRepository;
import com.skill2job.service.MapRepoAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** XP awarding (once-keys, streak days, best-effort), leaderboard ordering and name privacy. */
class GamificationServiceTest {

    final Map<String, Object> profileStore = new LinkedHashMap<>(), xpStore = new LinkedHashMap<>();
    final StudentProfileRepository base = MapRepoAccess.of(StudentProfileRepository.class, profileStore);
    final XpEventRepository xpRepo = MapRepoAccess.of(XpEventRepository.class, xpStore);
    boolean brokenSave = false;

    /** The in-memory repo does not know the Top20 query: answer it here with the same meaning. */
    final StudentProfileRepository profiles = (StudentProfileRepository) Proxy.newProxyInstance(
            StudentProfileRepository.class.getClassLoader(), new Class<?>[]{StudentProfileRepository.class}, (p, m, a) -> {
                if (m.getName().equals("findTop20ByXpGreaterThanOrderByXpDesc")) {
                    int min = (Integer) a[0];
                    return profileStore.values().stream().map(o -> (StudentProfile) o).filter(s -> s.getXp() > min)
                            .sorted(Comparator.comparingInt(StudentProfile::getXp).reversed()).limit(20).toList();
                }
                if (m.getName().equals("save") && brokenSave) throw new IllegalStateException("db down");
                try { return m.invoke(base, a); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
            });

    final GamificationService svc = new GamificationService(profiles, xpRepo, null, null, null, null);

    private StudentProfile student(String id, String name, int xp) {
        StudentProfile p = new StudentProfile();
        p.setUserId(id);
        p.setFullName(name);
        p.setXp(xp);
        profileStore.put(id, p);
        base.save(p);
        return p;
    }

    @Test
    void awardAddsXpAndLogsAnEvent() {
        StudentProfile p = student("s1", "Rahim Uddin", 0);
        svc.award("s1", 15, "Added a skill", null);
        assertEquals(15, p.getXp());
        assertEquals(1, xpStore.size());
        assertEquals(15, ((XpEvent) xpStore.values().iterator().next()).getXp());
    }

    @Test
    void aOnceKeyGrantsXpOnlyTheFirstTime() {
        StudentProfile p = student("s1", "Rahim Uddin", 0);
        svc.award("s1", 10, "skill java", "skill:java");
        svc.award("s1", 10, "skill java again", "skill:java");
        assertEquals(10, p.getXp(), "re-adding the same skill must not farm XP");
        assertEquals(1, xpStore.size());
        svc.award("s1", 10, "other skill", "skill:sql");
        assertEquals(20, p.getXp());
    }

    @Test
    void withoutAKeyEveryAwardCounts() {
        StudentProfile p = student("s1", "Rahim Uddin", 0);
        svc.award("s1", 5, "a", null);
        svc.award("s1", 5, "b", null);
        assertEquals(10, p.getXp());
    }

    @Test
    void zeroOrNegativeXpIsNeverGrantedButTodayStillCountsForTheStreak() {
        StudentProfile p = student("s1", "Rahim Uddin", 7);
        svc.award("s1", 0, "nothing", "k");
        svc.award("s1", -50, "penalty", null);
        assertEquals(7, p.getXp());
        assertTrue(xpStore.isEmpty());
        assertEquals(List.of(GamificationService.today().toString()), p.getActivityDays());
        assertFalse(p.getAwardedKeys().contains("k"), "a zero award must not burn the once-key");
    }

    @Test
    void todayIsRecordedOnceEvenAfterManyAwards() {
        StudentProfile p = student("s1", "Rahim Uddin", 0);
        for (int i = 0; i < 5; i++) svc.award("s1", 1, "x", null);
        assertEquals(1, p.getActivityDays().size());
    }

    @Test
    void activityHistoryIsCappedAtTheNewest120Days() {
        StudentProfile p = student("s1", "Rahim Uddin", 0);
        LocalDate d = LocalDate.of(2020, 1, 1);
        List<String> old = new ArrayList<>();
        for (int i = 0; i < 125; i++) old.add(d.plusDays(i).toString());
        p.setActivityDays(new ArrayList<>(old));
        svc.award("s1", 1, "x", null);
        assertEquals(120, p.getActivityDays().size());
        assertEquals(GamificationService.today().toString(), p.getActivityDays().get(119), "today is kept");
        assertEquals(old.get(6), p.getActivityDays().get(0), "the oldest days are dropped");
    }

    @Test
    void unknownStudentOrBrokenStorageNeverFailsTheCaller() {
        svc.award("ghost", 10, "x", null);
        assertTrue(xpStore.isEmpty());
        student("s1", "Rahim Uddin", 0);
        brokenSave = true;
        assertDoesNotThrow(() -> svc.award("s1", 10, "x", null)); // XP is best-effort
    }

    @Test
    void leaderboardIsOrderedRankedHidesZeroXpAndCapsAtTwenty() {
        student("a", "Amina Khatun", 50);
        student("b", "Bashir", 300);
        student("c", "Zero Xp", 0);
        for (int i = 0; i < 25; i++) student("x" + i, "User " + i, 100 + i);
        var rows = svc.leaderboard();
        assertEquals(20, rows.size());
        assertEquals("b", rows.get(0).studentId());
        assertEquals(1, rows.get(0).rank());
        assertEquals(20, rows.get(19).rank());
        for (int i = 1; i < rows.size(); i++) assertTrue(rows.get(i - 1).xp() >= rows.get(i).xp());
        assertTrue(rows.stream().noneMatch(r -> r.studentId().equals("c")), "0 XP is not ranked");
    }

    @Test
    void leaderboardShowsOnlyAFirstNameAndLastInitial() {
        student("a", "Rahim Uddin Ahmed", 10);
        var row = svc.leaderboard().get(0);
        assertEquals("Rahim A.", row.name());
    }

    @Test
    void displayNameHandlesMissingAndSingleNames() {
        assertEquals("Student", GamificationService.displayName(null));
        assertEquals("Student", GamificationService.displayName("   "));
        assertEquals("Madonna", GamificationService.displayName("  Madonna "));
        assertEquals("Rahim U.", GamificationService.displayName("Rahim   Uddin"));
    }
}

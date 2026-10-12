package com.skill2job.service;

import com.skill2job.dto.CountItem;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Payment;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.User;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.PaymentRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin "Payments" tab: revenue totals, status counts, monthly revenue and the latest transactions. */
@Service
public class AdminPaymentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final PaymentRepository paymentRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public AdminPaymentService(PaymentRepository paymentRepository, EmployerProfileRepository employerProfileRepository,
                               StudentProfileRepository studentProfileRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
    }

    public record PaymentRow(String id, String tranId, String payerName, Role payerRole, String purpose, double amount, String currency,
                             String status, String paymentMethod, Instant createdAt, Instant paidAt) { }

    public record Summary(double totalRevenue, double jobPostRevenue, double subscriptionRevenue, double studentProRevenue,
                          long successful, long failed, long cancelled, long pending, long activeProSubscribers,
                          long activeProStudents,
                          List<CountItem> revenuePerMonth, List<PaymentRow> recent) { }

    public Summary summary() {
        List<Payment> all = paymentRepository.findAll();
        List<EmployerProfile> employers = employerProfileRepository.findAll();
        Map<String, String> companies = employers.stream()
                .collect(Collectors.toMap(EmployerProfile::getUserId, p -> p.getCompanyName() == null ? "-" : p.getCompanyName(), (a, b) -> a));

        Map<String, String> studentNames = new LinkedHashMap<>();
        all.stream().filter(p -> p.payerRole() == Role.STUDENT && p.getStudentId() != null)
                .map(Payment::getStudentId).distinct().limit(200)
                .forEach(id -> userRepository.findById(id).map(User::getName).ifPresent(n -> studentNames.put(id, n)));

        List<Payment> ok = all.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS).toList();
        double total = ok.stream().mapToDouble(Payment::getAmount).sum();
        double jobs = ok.stream().filter(p -> p.getPurpose() == PaymentPurpose.JOB_POST).mapToDouble(Payment::getAmount).sum();
        double subs = ok.stream().filter(p -> p.getPurpose() == PaymentPurpose.SUBSCRIPTION).mapToDouble(Payment::getAmount).sum();
        double students = ok.stream().filter(p -> p.getPurpose() == PaymentPurpose.STUDENT_PRO).mapToDouble(Payment::getAmount).sum();

        Map<YearMonth, Double> months = new LinkedHashMap<>();
        YearMonth now = YearMonth.now(ZONE);
        for (int i = 5; i >= 0; i--) {
            months.put(now.minusMonths(i), 0.0);
        }
        for (Payment p : ok) {
            Instant when = p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt();
            if (when != null) {
                months.computeIfPresent(YearMonth.from(when.atZone(ZONE)), (k, v) -> v + p.getAmount());
            }
        }
        List<CountItem> perMonth = new ArrayList<>();
        months.forEach((k, v) -> perMonth.add(new CountItem(k.toString(), Math.round(v))));

        List<PaymentRow> recent = all.stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(25)
                .map(p -> new PaymentRow(p.getId(), p.getTranId(), payerName(p, companies, studentNames), p.payerRole(),
                        p.getPurpose() == null ? null : p.getPurpose().name(), p.getAmount(), p.getCurrency(),
                        p.getStatus() == null ? null : p.getStatus().name(), p.getPaymentMethod(), p.getCreatedAt(), p.getPaidAt()))
                .toList();

        Map<PaymentStatus, Long> byStatus = all.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));
        return new Summary(Math.round(total * 100.0) / 100.0, Math.round(jobs * 100.0) / 100.0, Math.round(subs * 100.0) / 100.0,
                Math.round(students * 100.0) / 100.0,
                byStatus.getOrDefault(PaymentStatus.SUCCESS, 0L), byStatus.getOrDefault(PaymentStatus.FAILED, 0L),
                byStatus.getOrDefault(PaymentStatus.CANCELLED, 0L), byStatus.getOrDefault(PaymentStatus.PENDING, 0L),
                employers.stream().filter(EmployerProfile::isProActive).count(),
                studentProfileRepository.findAll().stream().filter(StudentProfile::isProActive).count(), perMonth, recent);
    }

    /** Company name for employer payments, the student's name for student payments - never a mix-up. */
    private static String payerName(Payment p, Map<String, String> companies, Map<String, String> studentNames) {
        if (p.payerRole() == Role.STUDENT) {
            return studentNames.getOrDefault(p.getStudentId(), "-");
        }
        return companies.getOrDefault(p.getEmployerId(), "-");
    }
}

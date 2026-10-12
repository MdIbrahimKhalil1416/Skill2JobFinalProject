package com.skill2job.audit;

import com.skill2job.dto.PageResponse;
import com.skill2job.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes and reads the admin audit log. Most entries come from AuditAspect (@Audited methods);
 * a few flows that are not a single bean method call - payment callbacks, password changes - call
 * record(...) directly. Writing is best-effort: a failure to audit never breaks the user's action.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL = 300;
    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final AuditLogRepository repository;
    private final MongoTemplate mongoTemplate;

    public AuditService(AuditLogRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Records an action by whoever is currently authenticated (or SYSTEM if nobody is). */
    public void record(String action, String targetType, String targetId, String details, boolean success, String error) {
        record(currentUser(), action, targetType, targetId, details, success, error);
    }

    /** Records an action by an explicit actor (null actor = SYSTEM). */
    public void record(AuthUser actor, String action, String targetType, String targetId, String details,
                       boolean success, String error) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAt(Instant.now());
            if (actor != null) {
                entry.setActorId(actor.id());
                entry.setActorEmail(actor.email());
                entry.setActorRole(actor.role() == null ? null : actor.role().name());
            } else {
                entry.setActorRole("SYSTEM");
                entry.setActorEmail("system");
            }
            entry.setAction(action);
            entry.setTargetType(blankToNull(targetType));
            entry.setTargetId(blankToNull(targetId));
            entry.setDetails(truncate(details));
            entry.setSuccess(success);
            entry.setErrorMessage(truncate(error));
            entry.setIpAddress(currentIp());
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Could not write audit log entry for {}: {}", action, e.getMessage());
        }
    }

    /** Filtered, newest-first page of the log for the admin UI. */
    public PageResponse<AuditLogResponse> search(String action, String actor, Boolean success,
                                                 LocalDate from, LocalDate to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        List<Criteria> parts = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            parts.add(Criteria.where("action").is(action.trim()));
        }
        if (actor != null && !actor.isBlank()) {
            parts.add(Criteria.where("actorEmail").regex(Pattern.compile(Pattern.quote(actor.trim()), Pattern.CASE_INSENSITIVE)));
        }
        if (success != null) {
            parts.add(Criteria.where("success").is(success));
        }
        if (from != null || to != null) {
            Criteria at = Criteria.where("at");
            if (from != null) {
                at = at.gte(from.atStartOfDay(ZONE).toInstant());
            }
            if (to != null) {
                at = at.lt(to.plusDays(1).atStartOfDay(ZONE).toInstant());
            }
            parts.add(at);
        }
        Query query = parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, AuditLog.class);
        query.with(Sort.by(Sort.Direction.DESC, "at")).skip((long) safePage * safeSize).limit(safeSize);
        List<AuditLogResponse> content = mongoTemplate.find(query, AuditLog.class).stream()
                .map(AuditLogResponse::from).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    /** Every distinct action name recorded so far (for the filter dropdown). */
    public List<String> actions() {
        List<String> list = new ArrayList<>(mongoTemplate.findDistinct(new Query(), "action", AuditLog.class, String.class));
        list.sort(String::compareTo);
        return list;
    }

    static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext() == null ? null
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    private static String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_DETAIL ? s : s.substring(0, MAX_DETAIL - 1) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** API view of one entry. */
    public record AuditLogResponse(String id, Instant at, String actorId, String actorEmail, String actorRole,
                                   String action, String targetType, String targetId, String details,
                                   boolean success, String errorMessage, String ipAddress) {
        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getAt(), a.getActorId(), a.getActorEmail(), a.getActorRole(),
                    a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetails(), a.isSuccess(),
                    a.getErrorMessage(), a.getIpAddress());
        }
    }
}

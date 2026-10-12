package com.skill2job.audit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Spring AOP aspect behind @Audited: after every annotated method returns (or throws), one AuditLog
 * entry is written via AuditService. Cross-cutting by design - the audited services contain no
 * logging code of their own, which is the point of using AOP here.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(audited)", argNames = "jp,audited")
    public void afterSuccess(JoinPoint jp, Audited audited) {
        auditService.record(audited.action(), audited.targetType(), arg(jp, audited.targetArg()),
                arg(jp, audited.detailArg()), true, null);
    }

    @AfterThrowing(pointcut = "@annotation(audited)", throwing = "error", argNames = "jp,audited,error")
    public void afterFailure(JoinPoint jp, Audited audited, Throwable error) {
        auditService.record(audited.action(), audited.targetType(), arg(jp, audited.targetArg()),
                arg(jp, audited.detailArg()), false, error.getMessage());
    }

    private static String arg(JoinPoint jp, int index) {
        Object[] args = jp.getArgs();
        if (index < 0 || args == null || index >= args.length || args[index] == null) {
            return null;
        }
        return String.valueOf(args[index]);
    }
}

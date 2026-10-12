package com.skill2job.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public Spring-bean method as an auditable admin/business action. AuditAspect records one
 * AuditLog entry every time the method finishes - successfully or with an exception - including who
 * did it (from the SecurityContext), the target id and a short detail string.
 *
 * Only works when the method is called through the Spring proxy (i.e. from another bean, such as a
 * controller) - the usual Spring AOP rule. Arguments are never logged wholesale (they could contain
 * passwords); only the ones named by targetArg / detailArg are.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** Short machine-friendly action name, e.g. "JOB_DELETE". Shown and filterable in the admin UI. */
    String action();

    /** What kind of thing was acted on, e.g. "JOB", "USER". */
    String targetType() default "";

    /** Index of the method argument holding the target id (-1 = none). */
    int targetArg() default -1;

    /** Index of an argument whose toString() becomes the detail text (-1 = none). */
    int detailArg() default -1;
}

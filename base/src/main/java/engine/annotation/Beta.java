package engine.annotation;

import java.lang.annotation.*;

/**
 * Annotate any beta code.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.METHOD,
        ElementType.FIELD, ElementType.PACKAGE, ElementType.MODULE})
@Documented
public @interface Beta {
}

package temper.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotated element could be null under some circumstances. */
@Documented
@Target({
    ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD,
    ElementType.PARAMETER, ElementType.LOCAL_VARIABLE
})
@Retention(RetentionPolicy.CLASS)
public @interface Nullable {
}

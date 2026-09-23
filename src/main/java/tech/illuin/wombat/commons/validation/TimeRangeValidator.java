package tech.illuin.wombat.commons.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tech.illuin.wombat.core.activity.commons.TimeRange;

public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRange>
{
    @Override
    public boolean isValid(TimeRange value, ConstraintValidatorContext context)
    {
        if (value == null)
            return true;
        return value.start() != null && value.end() != null && value.start().isBefore(value.end());
    }
}

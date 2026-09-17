package it.bancaditalia.quarkus.poc.jakarta.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** ISO 13616 shape and mod-97 check digits. Null is valid: presence is @NotNull's job. */
public class IbanValidator implements ConstraintValidator<Iban, String> {

    private static final Pattern SHAPE = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!SHAPE.matcher(value).matches()) {
            return false;
        }
        String rearranged = value.substring(4) + value.substring(0, 4);
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            int digit = Character.digit(rearranged.charAt(i), 36);
            remainder = digit < 10 ? (remainder * 10 + digit) % 97 : (remainder * 100 + digit) % 97;
        }
        return remainder == 1;
    }
}

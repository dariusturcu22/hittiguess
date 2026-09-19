package org.dariusturcu.backend.model.playlist;

import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePlaylistRequestValidationTest {

    private static final String APPROVED_COLOR = "cba6f7";
    private static final String UNAPPROVED_HEX_COLOR = "000000";
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAnApprovedPlaylistColor() {
        assertThat(validationErrorsFor(APPROVED_COLOR)).isEmpty();
    }

    @Test
    void rejectsAValidHexColorOutsideTheApprovedPalette() {
        assertThat(validationErrorsFor(UNAPPROVED_HEX_COLOR)).isNotEmpty();
    }

    private Set<ConstraintViolation<UpdatePlaylistRequest>> validationErrorsFor(String color) {
        return validator.validate(new UpdatePlaylistRequest("Playlist", color));
    }
}

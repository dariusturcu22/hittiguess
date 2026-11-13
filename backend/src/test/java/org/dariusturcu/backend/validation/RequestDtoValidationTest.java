package org.dariusturcu.backend.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.dariusturcu.backend.model.admin.ProposalBatchRequest;
import org.dariusturcu.backend.model.admin.RecheckSongsRequest;
import org.dariusturcu.backend.model.admin.RunNowRequest;
import org.dariusturcu.backend.model.auth.LoginRequest;
import org.dariusturcu.backend.model.auth.PasswordResetConfirmRequest;
import org.dariusturcu.backend.model.auth.RegisterRequest;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.SendChatMessageRequest;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.session.StartSessionWithSongsRequest;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.ExpandPlaylistLinkRequest;
import org.dariusturcu.backend.model.song.SubmitReportRequest;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.user.UpdateUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    void createSongRequestEnforcesLengthAndFormatLimits() {
        String oversizedArtist = "a".repeat(256);
        CreateSongRequest request = new CreateSongRequest(
                oversizedArtist, "Valid Title", 2000, "dQw4w9WgXcQ", "cba6f7", null);

        Set<ConstraintViolation<CreateSongRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("artist"));
    }

    @Test
    void updateSongRequestEnforcesTitleLength() {
        String oversizedTitle = "t".repeat(256);
        UpdateSongRequest request = new UpdateSongRequest(
                "Valid Artist", oversizedTitle, 2000, "dQw4w9WgXcQ", "cba6f7", null);

        Set<ConstraintViolation<UpdateSongRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("title"));
    }

    @Test
    void updatePlaylistRequestEnforcesNameLength() {
        String oversizedName = "n".repeat(256);
        UpdatePlaylistRequest request = new UpdatePlaylistRequest(oversizedName, "cba6f7", "A valid description");

        Set<ConstraintViolation<UpdatePlaylistRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("name"));
    }

    @Test
    void bulkImportRequestEnforcesBounds() {
        List<String> oversizedList = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            oversizedList.add("dQw4w9WgXcQ");
        }
        BulkImportRequest request = new BulkImportRequest(
                "https://www.youtube.com/playlist?list=PL123", oversizedList, 1L, "job-1");

        Set<ConstraintViolation<BulkImportRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("videoIdsOrLinks"));
    }

    @Test
    void expandPlaylistLinkRequestEnforcesNotBlankAndLength() {
        ExpandPlaylistLinkRequest blankRequest = new ExpandPlaylistLinkRequest("   ");
        Set<ConstraintViolation<ExpandPlaylistLinkRequest>> blankViolations = validator.validate(blankRequest);
        assertThat(blankViolations).isNotEmpty();

        String oversizedLink = "https://youtube.com/" + "a".repeat(2000);
        ExpandPlaylistLinkRequest oversizedRequest = new ExpandPlaylistLinkRequest(oversizedLink);
        Set<ConstraintViolation<ExpandPlaylistLinkRequest>> oversizedViolations = validator.validate(oversizedRequest);
        assertThat(oversizedViolations).isNotEmpty();
    }

    @Test
    void submitReportRequestEnforcesMessageBounds() {
        String oversizedMessage = "m".repeat(2001);
        SubmitReportRequest request = new SubmitReportRequest(oversizedMessage, 2000, "sources");

        Set<ConstraintViolation<SubmitReportRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("message"));
    }

    @Test
    void updateUserRequestEnforcesEmailAndUsernameBounds() {
        UpdateUserRequest invalidEmail = new UpdateUserRequest("validuser", "notanemail", null);
        Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(invalidEmail);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("email"));
    }

    @Test
    void registerRequestEnforcesPasswordLength() {
        RegisterRequest shortPassword = new RegisterRequest("testuser", "test@example.com", "12345");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(shortPassword);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @Test
    void loginRequestEnforcesEmailAndPasswordLimits() {
        String oversizedPassword = "p".repeat(129);
        LoginRequest request = new LoginRequest("user@example.com", oversizedPassword, false);

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @Test
    void joinPlaylistRequestEnforcesDisplayNameAndAvatarPattern() {
        JoinPlaylistRequest invalidAvatar = new JoinPlaylistRequest("player", "https://malicious.site/avatar.png");

        Set<ConstraintViolation<JoinPlaylistRequest>> violations = validator.validate(invalidAvatar);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("avatarUrl"));
    }

    @Test
    void sendChatMessageRequestEnforcesLengthLimit() {
        String oversizedMessage = "c".repeat(501);
        SendChatMessageRequest request = new SendChatMessageRequest(oversizedMessage);

        Set<ConstraintViolation<SendChatMessageRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("content"));
    }

    @Test
    void runNowRequestEnforcesYoutubeIdPattern() {
        RunNowRequest invalidId = new RunNowRequest("invalid-id");

        Set<ConstraintViolation<RunNowRequest>> violations = validator.validate(invalidId);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("youtubeId"));
    }

    @Test
    void submitReportRequestEnforcesSourcesLength() {
        SubmitReportRequest request = new SubmitReportRequest("Wrong year", 2000, "s".repeat(2001));

        Set<ConstraintViolation<SubmitReportRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("sources"));
    }

    @Test
    void titleArtistGuessRequestEnforcesGuessLength() {
        String overlongGuess = "g".repeat(TitleArtistGuessRequest.MAX_GUESS_LENGTH + 1);
        TitleArtistGuessRequest request = new TitleArtistGuessRequest(overlongGuess, overlongGuess);

        Set<ConstraintViolation<TitleArtistGuessRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("guessedArtist"));
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("guessedTitle"));
    }

    @Test
    void joinPlaylistRequestEnforcesTheDisplayNameRules() {
        JoinPlaylistRequest controlCharacters = new JoinPlaylistRequest("bad\u0007name", null);
        JoinPlaylistRequest tooLong = new JoinPlaylistRequest("n".repeat(31), null);

        assertThat(validator.validate(controlCharacters))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
        assertThat(validator.validate(tooLong))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
        assertThat(validator.validate(new JoinPlaylistRequest("\u0007", null)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
        assertThat(validator.validate(new JoinGroupRequest(null, null, "Player\u0007", null)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
        assertThat(validator.validate(new JoinPlaylistRequest("Player One", null))).isEmpty();
    }

    @Test
    void joinGroupRequestEnforcesCodeLengths() {
        JoinGroupRequest request = new JoinGroupRequest("i".repeat(65), "j".repeat(65), null, null);

        Set<ConstraintViolation<JoinGroupRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("inviteCode"));
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("joinCode"));
    }

    @Test
    void registerRequestEnforcesEmailAndPasswordMaximums() {
        String overlongEmail = "e".repeat(250) + "@example.com";
        RegisterRequest request = new RegisterRequest("testuser", overlongEmail, "p".repeat(129));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("email"));
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("password"));
    }

    @Test
    void passwordResetConfirmRequestEnforcesTokenAndPasswordBounds() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("t".repeat(513), "p".repeat(129));

        Set<ConstraintViolation<PasswordResetConfirmRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("token"));
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void startPlaylistImportRequestEnforcesListSizeAndEntryLength() {
        List<String> tooManyEntries = Collections.nCopies(201, "dQw4w9WgXcQ");
        StartPlaylistImportRequest tooMany = new StartPlaylistImportRequest(tooManyEntries, null);
        StartPlaylistImportRequest overlongEntry = new StartPlaylistImportRequest(List.of("l".repeat(2001)), null);

        assertThat(validator.validate(tooMany))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("videoIdsOrLinks"));
        assertThat(validator.validate(overlongEntry))
                .anyMatch(violation -> violation.getPropertyPath().toString().startsWith("videoIdsOrLinks["));
    }

    @Test
    void startSessionWithSongsRequestEnforcesListBounds() {
        StartSessionWithSongsRequest empty = new StartSessionWithSongsRequest(List.of());
        StartSessionWithSongsRequest tooMany = new StartSessionWithSongsRequest(Collections.nCopies(201, 1L));

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(tooMany)).isNotEmpty();
    }

    @Test
    void recheckSongsRequestEnforcesListSize() {
        RecheckSongsRequest request = new RecheckSongsRequest(Collections.nCopies(1001, 1L), false);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("songIds"));
    }

    @Test
    void proposalBatchRequestEnforcesMaximumSize() {
        ProposalBatchRequest request = new ProposalBatchRequest(
                Collections.nCopies(ProposalBatchRequest.MAX_SONG_IDS + 1, 1L));

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("songIds"));
    }

    @Test
    void proposalBatchRequestEnforcesNotEmpty() {
        ProposalBatchRequest emptyRequest = new ProposalBatchRequest(List.of());

        Set<ConstraintViolation<ProposalBatchRequest>> violations = validator.validate(emptyRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("songIds"));
    }
}

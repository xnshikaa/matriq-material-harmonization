package harmonizer.api;

import harmonizer.model.InvalidDatasetException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.List;

/** Turns every expected failure into a clear JSON error instead of a stack trace or a silent wrong answer. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String error, List<String> details) {
    }

    @ExceptionHandler(InvalidDatasetException.class)
    public ResponseEntity<ApiError> invalidDataset(InvalidDatasetException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("Invalid dataset", e.getProblems()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("Bad request", List.of(e.getMessage())));
    }

    /** No dataset uploaded yet. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("Not ready", List.of(e.getMessage())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("File too large", List.of("The uploaded file exceeds the size limit.")));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> unreadable(IOException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("Could not read the upload", List.of(String.valueOf(e.getMessage()))));
    }
}

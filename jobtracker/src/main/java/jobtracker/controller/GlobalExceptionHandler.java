package jobtracker.controller;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import jobtracker.dto.error.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of("Not Found", ex.getMessage()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of("Not Found", "Resource not found"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
			.toList();
		return ResponseEntity.badRequest()
			.body(ApiError.of("Validation Failed", "Invalid request payload", details));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiError> handleBind(BindException ex) {
		List<String> details = ex.getFieldErrors().stream()
			.map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
			.toList();
		return ResponseEntity.badRequest()
			.body(ApiError.of("Validation Failed", "Invalid request payload", details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
			.body(ApiError.of("Bad Request", "Malformed request body"));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest()
			.body(ApiError.of("Bad Request", "Invalid value for parameter '" + ex.getName() + "'"));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.badRequest()
			.body(ApiError.of("Bad Request", "Missing required parameter: " + ex.getParameterName()));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
		String reason = ex.getReason() != null ? ex.getReason() : resolved.getReasonPhrase();
		return ResponseEntity.status(resolved).body(ApiError.of(resolved.getReasonPhrase(), reason));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiError.of("Conflict", "The request conflicts with existing data"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiError.of("Internal Server Error", "An unexpected error occurred"));
	}
}
package jobtracker.dto.error;

import java.util.List;

public record ApiError(String error, String message, List<String> details) {

	public static ApiError of(String error, String message) {
		return new ApiError(error, message, List.of());
	}

	public static ApiError of(String error, String message, List<String> details) {
		return new ApiError(error, message, details);
	}
}
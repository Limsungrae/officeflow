package officeflow.program;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgramForm(
        @NotBlank(message = "프로그램명을 입력해주세요.")
        @Size(max = 200, message = "프로그램명은 200자 이내로 입력해주세요.")
        String title,

        @NotNull(message = "시작일을 입력해주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해주세요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Size(max = 200, message = "대상은 200자 이내로 입력해주세요.")
        String targetGroup,

        @Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
        Integer capacity,

        @Size(max = 100, message = "담당자는 100자 이내로 입력해주세요.")
        String manager,

        @NotNull(message = "상태를 선택해주세요.")
        ProgramStatus status) {

    public boolean hasInvalidDateRange() {
        return startDate != null && endDate != null && endDate.isBefore(startDate);
    }
}

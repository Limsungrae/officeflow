package officeflow.program;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(name = "target_group", length = 200)
    private String target;

    private Integer capacity;

    @Column(name = "manager_name", length = 100)
    private String manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProgramStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Program() {
    }

    public Program(String title, LocalDate startDate, LocalDate endDate, String target,
                   Integer capacity, String manager, ProgramStatus status) {
        this.title = requireTitle(title);
        validateDates(startDate, endDate);
        validateCapacity(capacity);
        this.startDate = startDate;
        this.endDate = endDate;
        this.target = normalize(target);
        this.capacity = capacity;
        this.manager = normalize(manager);
        this.status = status == null ? ProgramStatus.PLANNED : status;
    }

    public void changeStatus(ProgramStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("프로그램 상태는 비어 있을 수 없습니다.");
        }
        this.status = status;
    }

    public void updateDetails(String title, LocalDate startDate, LocalDate endDate,
                              String target, Integer capacity, String manager) {
        this.title = requireTitle(title);
        validateDates(startDate, endDate);
        validateCapacity(capacity);
        this.startDate = startDate;
        this.endDate = endDate;
        this.target = normalize(target);
        this.capacity = capacity;
        this.manager = normalize(manager);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("프로그램명은 필수입니다.");
        }
        return value.trim();
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private static void validateCapacity(Integer capacity) {
        if (capacity != null && capacity < 0) {
            throw new IllegalArgumentException("정원은 0명 이상이어야 합니다.");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getTarget() {
        return target;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public String getManager() {
        return manager;
    }

    public ProgramStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

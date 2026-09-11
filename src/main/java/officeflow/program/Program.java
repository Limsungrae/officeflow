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
import jakarta.persistence.Table;

@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 200)
    private String targetGroup;

    private Integer capacity;

    @Column(length = 100)
    private String manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProgramStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Program() {
    }

    private Program(String title, LocalDate startDate, LocalDate endDate, String targetGroup,
            Integer capacity, String manager, ProgramStatus status) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.targetGroup = targetGroup;
        this.capacity = capacity;
        this.manager = manager;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public static Program create(ProgramForm form) {
        return new Program(form.title().trim(), form.startDate(), form.endDate(), clean(form.targetGroup()),
                form.capacity(), clean(form.manager()), form.status());
    }

    public void update(ProgramForm form) {
        this.title = form.title().trim();
        this.startDate = form.startDate();
        this.endDate = form.endDate();
        this.targetGroup = clean(form.targetGroup());
        this.capacity = form.capacity();
        this.manager = clean(form.manager());
        this.status = form.status();
    }

    public ProgramForm toForm() {
        return new ProgramForm(title, startDate, endDate, targetGroup, capacity, manager, status);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getTargetGroup() { return targetGroup; }
    public Integer getCapacity() { return capacity; }
    public String getManager() { return manager; }
    public ProgramStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

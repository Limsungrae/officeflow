package officeflow.program;

public enum ProgramStatus {
    PLANNED("예정"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    CANCELLED("취소");

    private final String label;

    ProgramStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

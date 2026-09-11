package officeflow.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:programtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
class ProgramPersistenceTest {

    @Autowired ProgramRepository programRepository;
    @Autowired ProgramService programService;

    @BeforeEach
    void clear() {
        programRepository.deleteAll();
    }

    @Test
    void createsAndLoadsProgramFromH2() {
        Program saved = programService.create(form("길 위의 인문학", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));

        assertThat(saved.getId()).isNotNull();
        Program loaded = programService.findById(saved.getId());
        assertThat(loaded.getTitle()).isEqualTo("길 위의 인문학");
        assertThat(loaded.getCapacity()).isEqualTo(20);
        assertThat(loaded.getStatus()).isEqualTo(ProgramStatus.PLANNED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void updatesAndDeletesProgram() {
        Program saved = programService.create(form("기존 프로그램", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)));

        ProgramForm updated = new ProgramForm("수정 프로그램", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                "성남시민", 30, "담당자B", ProgramStatus.IN_PROGRESS);
        programService.update(saved.getId(), updated);

        Program loaded = programService.findById(saved.getId());
        assertThat(loaded.getTitle()).isEqualTo("수정 프로그램");
        assertThat(loaded.getCapacity()).isEqualTo(30);
        assertThat(loaded.getStatus()).isEqualTo(ProgramStatus.IN_PROGRESS);

        programService.delete(saved.getId());
        assertThat(programRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void rejectsEndDateBeforeStartDateWithoutSaving() {
        ProgramForm invalid = form("잘못된 기간", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> programService.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종료일은 시작일보다 빠를 수 없습니다.");
        assertThat(programRepository.count()).isZero();
    }

    private ProgramForm form(String title, LocalDate startDate, LocalDate endDate) {
        return new ProgramForm(title, startDate, endDate, "성인", 20, "담당자A", ProgramStatus.PLANNED);
    }
}

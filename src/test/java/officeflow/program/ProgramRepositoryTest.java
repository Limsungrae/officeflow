package officeflow.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false)
class ProgramRepositoryTest {

    @Autowired
    private ProgramRepository programRepository;

    @Test
    void savesAndLoadsProgramWithGeneratedIdAndTimestamps() {
        Program program = new Program(
                "2026 길 위의 인문학",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                "성남시민",
                30,
                "중원도서관",
                ProgramStatus.OPEN);

        Program saved = programRepository.saveAndFlush(program);
        Program found = programRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getTitle()).isEqualTo("2026 길 위의 인문학");
        assertThat(found.getTarget()).isEqualTo("성남시민");
        assertThat(found.getCapacity()).isEqualTo(30);
        assertThat(found.getStatus()).isEqualTo(ProgramStatus.OPEN);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void returnsProgramsInLatestStartDateOrder() {
        programRepository.save(new Program("이전 프로그램", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, 20, null, ProgramStatus.COMPLETED));
        programRepository.save(new Program("최근 프로그램", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 20, null, ProgramStatus.PLANNED));
        programRepository.flush();

        assertThat(programRepository.findAllByOrderByStartDateDescIdDesc())
                .extracting(Program::getTitle)
                .containsExactly("최근 프로그램", "이전 프로그램");
    }

    @Test
    void protectsBasicProgramInvariantsBeforePersistence() {
        assertThatThrownBy(() -> new Program(" ", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로그램명은 필수입니다.");

        assertThatThrownBy(() -> new Program(
                "날짜 오류",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 1),
                null,
                null,
                null,
                ProgramStatus.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종료일은 시작일보다 빠를 수 없습니다.");
    }
}

package officeflow.program;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProgramService {

    private final ProgramRepository programRepository;

    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    public List<Program> findAll() {
        return programRepository.findAllByOrderByStartDateDescIdDesc();
    }

    public Program findById(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로그램을 찾을 수 없습니다."));
    }

    @Transactional
    public Program create(ProgramForm form) {
        validateDateRange(form);
        return programRepository.save(Program.create(form));
    }

    @Transactional
    public Program update(Long id, ProgramForm form) {
        validateDateRange(form);
        Program program = findById(id);
        program.update(form);
        return program;
    }

    @Transactional
    public void delete(Long id) {
        Program program = findById(id);
        programRepository.delete(program);
    }

    private void validateDateRange(ProgramForm form) {
        if (form.hasInvalidDateRange()) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}

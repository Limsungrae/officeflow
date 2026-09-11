package officeflow.program;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @ModelAttribute("statuses")
    public ProgramStatus[] statuses() {
        return ProgramStatus.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("programs", programService.findAll());
        return "program/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("programForm", new ProgramForm(null, null, null, null, null, null, ProgramStatus.PLANNED));
        model.addAttribute("formTitle", "프로그램 등록");
        model.addAttribute("formAction", "/programs");
        return "program/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("programForm") ProgramForm form,
            BindingResult bindingResult, Model model) {
        if (form.hasInvalidDateRange()) {
            bindingResult.reject("dateRange", "종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("formTitle", "프로그램 등록");
            model.addAttribute("formAction", "/programs");
            return "program/form";
        }
        Program saved = programService.create(form);
        return "redirect:/programs/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("program", programService.findById(id));
        return "program/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Program program = programService.findById(id);
        model.addAttribute("program", program);
        model.addAttribute("programForm", program.toForm());
        model.addAttribute("formTitle", "프로그램 수정");
        model.addAttribute("formAction", "/programs/" + id);
        return "program/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("programForm") ProgramForm form,
            BindingResult bindingResult, Model model) {
        if (form.hasInvalidDateRange()) {
            bindingResult.reject("dateRange", "종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("program", programService.findById(id));
            model.addAttribute("formTitle", "프로그램 수정");
            model.addAttribute("formAction", "/programs/" + id);
            return "program/form";
        }
        programService.update(id, form);
        return "redirect:/programs/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        programService.delete(id);
        return "redirect:/programs";
    }
}

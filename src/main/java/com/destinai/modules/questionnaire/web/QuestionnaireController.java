package com.destinai.modules.questionnaire.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class QuestionnaireController {
	@GetMapping("/questionnaire")
	public String questionnaire() {
		return "questionnaire/questionnaire";
	}
}


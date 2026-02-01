package com.destinai.modules.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
	@GetMapping("/")
	public String root() {
		return "redirect:/login";
	}

	@GetMapping("/login")
	public String login() {
		return "auth/login";
	}
}


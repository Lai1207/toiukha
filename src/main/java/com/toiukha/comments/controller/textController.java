package com.toiukha.comments.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/test")
public class textController {

	@GetMapping("/dotest")
	public String doTest() {
		return "/otherError";
	}
	
}

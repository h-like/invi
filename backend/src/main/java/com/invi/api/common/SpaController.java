package com.invi.api.common;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Explicit shell routes only: never swallow API errors or missing JS/CSS with index.html. */
@Controller
@Profile("https-test")
public class SpaController {
    @GetMapping(path = {"/", "/templates", "/templates/{id}", "/login", "/oauth/callback", "/create",
            "/invitations", "/e/{id}", "/e/{id}/guests", "/i/{slug}"}, produces = MediaType.TEXT_HTML_VALUE)
    public String shell() { return "forward:/index.html"; }

    @GetMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() { return "User-agent: *\nDisallow: /\n"; }
}

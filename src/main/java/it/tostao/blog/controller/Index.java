package it.tostao.blog.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;


@Controller
public class Index {

    private static final Logger LOG = LoggerFactory.getLogger(Index.class);

	public Index() {

    }

	@RequestMapping(value = { "/", "/index" }, method = RequestMethod.GET)
	public ModelAndView get(){
        LOG.info("LOG Info - Index called!");
        LOG.warn("LOG Warn -Index called!");
        LOG.trace("LOG Trace -Index called!");
        LOG.error("LOG Error -Index called!");
        LOG.debug("LOG Debug -Index called!");
        System.out.println("System out -Index called!");
		ModelAndView mav = new ModelAndView("index");
		return mav;
	}
}

package com.devsss.onlyone.server.ctrl;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PageCtrl {

    @RequestMapping("/")
    public String index(ModelMap modelMap) {
        modelMap.put("logo","OnlyOne");
        modelMap.put("title","OnlyOne");
        modelMap.put("menuValue","/");
        return "index";
    }

    /**
     * 连接管理
     * @param modelMap model
     * @return 页面名称
     */
    @RequestMapping("/lj")
    public String lj(ModelMap modelMap) {
        modelMap.put("logo","OnlyOne");
        modelMap.put("title","OnlyOne");
        modelMap.put("menuValue","/lj");
        return "index";
    }

    /**
     * 证书管理
     * @param modelMap model
     * @return 页面名称
     */
    @RequestMapping("/zs")
    public String zs(ModelMap modelMap) {
        modelMap.put("logo","OnlyOne");
        modelMap.put("title","OnlyOne");
        modelMap.put("menuValue","/zs");
        return "index";
    }
}

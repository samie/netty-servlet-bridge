/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.javaforge.netty.vaadin;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinServlet;
import javax.servlet.annotation.WebServlet;

/**
 * Vaadin application servlet bootstrap.
 *
 * @author Sami Ekblad
 */
@WebServlet(value = {"/*"}, asyncSupported = true)
@VaadinServletConfiguration(productionMode = false, ui = HelloWorldUI.class)
public class Servlet extends VaadinServlet {
}

/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Nikolay Gorodnov
 * Created: 22.10.2010 17:15:47
 *
 * $Id: DefaultApp.java 3262 2010-11-26 06:41:45Z krokhin $
 */
package com.haulmont.cuba.web;

import com.haulmont.cuba.client.sys.MessagesClientImpl;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.gui.AppConfig;
import com.haulmont.cuba.security.global.LoginException;
import com.haulmont.cuba.web.sys.ActiveDirectoryHelper;
import com.haulmont.cuba.web.toolkit.Timer;
import com.vaadin.service.ApplicationContext;
import com.vaadin.ui.Window;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

public class DefaultApp extends App implements ConnectionListener {

    private static Log log = LogFactory.getLog(DefaultApp.class);

    private boolean principalIsWrong;

    private static final long serialVersionUID = 70273562618123015L;

    // Login on start only on first request from user
    protected boolean tryLoginOnStart = true;

    @Override
    protected Connection createConnection() {
        Connection connection = new DefaultConnection();
        connection.addListener(this);
        return connection;
    }

    /**
     * Should be overridden in descendant to create an application-specific login window
     * @return Login form
     */
    protected LoginWindow createLoginWindow() {
        LoginWindow window = new LoginWindow(this, connection);

        Timer timer = createSessionPingTimer(false);
        if (timer != null)
            timers.add(timer, window);

        return window;
    }

    /**
     * Get or create new LoginWindow
     * @return LoginWindow
     */
    private LoginWindow getLoginWindow() {
        for (Window win : getWindows()) {
            if (win instanceof LoginWindow)
                return (LoginWindow) win;
        }

        return createLoginWindow();
    }

    @Override
    public void init() {
        log.debug("Initializing application");

        //todo AppConfig.addGroovyImport(PersistenceHelper.class);

        ApplicationContext appContext = getContext();
        appContext.addTransactionListener(this);

        LoginWindow window = createLoginWindow();
        setMainWindow(window);

        if (getTheme() == null) {
            String themeName = AppContext.getProperty(AppConfig.THEME_NAME_PROP);
            if (themeName == null) themeName = THEME_NAME;
            setTheme(themeName);
        }
    }

    @Override
    public Window getWindow(String name) {
        Window window = super.getWindow(name);

        // it does not exist yet, create it.
        if (window == null) {
            if (connection.isConnected()) {
                final AppWindow appWindow = createAppWindow();
                appWindow.setName(name);
                addWindow(appWindow);
                appWindow.focus();
                connection.addListener(appWindow);

                return appWindow;
            } else {
                final Window loginWindow = getLoginWindow();
                removeWindow(loginWindow);

                loginWindow.setName(name);

                addWindow(loginWindow);

                return loginWindow;
            }
        }

        return window;
    }

    @Override
    public void connectionStateChanged(Connection connection) throws LoginException {
        MessagesClientImpl messagesClient = AppBeans.get(Messages.NAME);

        if (connection.isConnected()) {
            log.debug("Creating AppWindow");

            getTimers().stopAll();

            for (Object win : new ArrayList<Object>(getWindows())) {
                removeWindow((Window) win);
            }

            messagesClient.setRemoteSearch(true);

            String name = currentWindowName.get();
            if (name == null)
                name = createWindowName(true);

            Window window = getWindow(name);

            setMainWindow(window);
            currentWindowName.set(window.getName());

            initExceptionHandlers(true);

            if (linkHandler != null) {
                linkHandler.handle();
                linkHandler = null;
            }
        }
        else {
            log.debug("Closing all windows");
            getWindowManager().closeAll();

            getTimers().stopAll();

            for (Object win : new ArrayList<Object>(getWindows())) {
                removeWindow((Window) win);
            }

            messagesClient.setRemoteSearch(false);

            String name = currentWindowName.get();
            if (name == null)
                name = createWindowName(false);

            Window window = createLoginWindow();
            window.setName(name);
            setMainWindow(window);

            currentWindowName.set(window.getName());

            initExceptionHandlers(false);
        }
    }

    @Override
    protected boolean loginOnStart(HttpServletRequest request) {
        if (tryLoginOnStart &&
                request.getUserPrincipal() != null
                && !principalIsWrong
                && ActiveDirectoryHelper.useActiveDirectory()) {

            String userName = request.getUserPrincipal().getName();
            log.debug("Trying to login ActiveDirectory as " + userName);
            try {
                ((ActiveDirectoryConnection) connection).loginActiveDirectory(userName, request.getLocale());
                principalIsWrong = false;

                return true;
            } catch (LoginException e) {
                principalIsWrong = true;
            } finally {
                // Close attempt login on start
                tryLoginOnStart = false;
            }
        }

        return false;
    }
}
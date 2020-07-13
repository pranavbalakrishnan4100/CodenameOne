/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores
 * CA 94065 USA or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package com.codename1.impl.javase;

import java.awt.EventQueue;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * A simple class that can invoke a lifecycle object to allow it to run a
 * Codename One application. Classes are loaded with a classloader so the UI
 * skin can be updated and the lifecycle objects reloaded.
 *
 * @author Shai Almog
 */
public class Simulator {
    
    private static final String DEFAULT_SKIN="/iPhoneX.skin";
    private static ClassPathLoader rootClassLoader;
    

    /**
     * Accepts the classname to launch
     */
    public static void main(final String[] argv) throws Exception {
        try {
            // Load the sqlite database Engine JDBC driver in the top level classloader so it's shared
            // this works around the exception: java.lang.UnsatisfiedLinkError: Native Library sqlite already loaded in another classloader
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
        }
        System.setProperty("NSHighResolutionCapable", "true");
        String skin = System.getProperty("dskin");
        if (skin == null) {
            System.setProperty("dskin", DEFAULT_SKIN);
        }
        
        for (int i = 0; i < argv.length; i++) {
            String argv1 = argv[i];
            if(argv1.equals("resetSkins")){
                System.setProperty("resetSkins", "true");
                System.setProperty("skin", DEFAULT_SKIN);
                System.setProperty("dskin", DEFAULT_SKIN);            
            }
        }
        
        if (System.getenv("CN1_SIMULATOR_SKIN") != null) {
            System.setProperty("skin", System.getenv("CN1_SIMULATOR_SKIN"));
        }
        
        StringTokenizer t = new StringTokenizer(System.getProperty("java.class.path"), File.pathSeparator);
        if(argv.length > 0) {
            System.setProperty("MainClass", argv[0]);
        }
        List<File> files = new ArrayList<File>();
        int len = t.countTokens();
        for (int iter = 0; iter < len; iter++) {
            files.add(new File(t.nextToken()));
        }
        File javase = new File("native" + File.separator + "javase");
        File libJavase = new File("lib" + File.separator + "impl" + File.separator + "native" + File.separator + "javase");
        for (File dir : new File[]{javase, libJavase}) {
            if (dir.exists()) {
                
                for (File jar : dir.listFiles()) {
                    if (jar.getName().endsWith(".jar")) {
                        if (!files.contains(jar)) {
                            files.add(jar);
                            System.setProperty("java.class.path", System.getProperty("java.class.path")+File.pathSeparator+jar.getAbsolutePath());
                        }
                    }
                }
            }
        }
        boolean cefSupported = false;
        boolean fxSupported = false;
        try {
            Class.forName("javafx.embed.swing.JFXPanel");
            fxSupported = true;
        } catch (Throwable ex) {}
        
        File cef = new File(System.getProperty("user.home") + File.separator + ".codenameone" + File.separator + "cef");
        if (cef.exists()) {
            cefSupported = true;
            System.out.println("Adding CEF to classpath");
            System.setProperty("java.class.path", System.getProperty("java.class.path") 
                    + File.pathSeparator + cef.getAbsolutePath());
            for (File jar : cef.listFiles()) {
                if (jar.getName().endsWith(".jar")) {
                    System.setProperty("java.class.path", System.getProperty("java.class.path") + File.pathSeparator + jar.getAbsolutePath());
                    files.add(jar);
                }
            }
        }
        
        File jmf = new File(System.getProperty("user.home") + File.separator + ".codenameone" + File.separator + "jmf-2.1.1e.jar");
        if (jmf.exists()) {
            System.setProperty("java.class.path", System.getProperty("java.class.path") + File.pathSeparator + jmf.getAbsolutePath());
            files.add(jmf);
        }
        
        String implementation = System.getProperty("cn1.javase.implementation", "");

        
        if (implementation.equalsIgnoreCase("cef") && !cefSupported) {
            // We will use CEF
            System.err.println("cn1.javase.implementation=cef but CEF was not found.  Please update your Codename One libraries and try again.\nAlternatively, you can try using a different implementation.");
            System.exit(1);
        }
        if (implementation.equalsIgnoreCase("fx") && !fxSupported) {
            System.err.println("cn1.javase.implementation=fx but JavaFX was not found.  Please use a JDK that has JavaFX such as ZuluFX.  https://www.azul.com/downloads/zulu-community/");
            System.exit(1);
        }
        if ("".equals(implementation)) {
            if (cefSupported) {
                System.setProperty("cn1.javase.implementation", "cef");
            } else if (fxSupported) {
                System.setProperty("cn1.javase.implementation", "fx");
            } else {
                System.setProperty("cn1.javase.implementation", "jmf");
            }
        }
        
        loadFXRuntime();
        ClassLoader ldr = rootClassLoader == null ? 
                new ClassPathLoader( files.toArray(new File[files.size()])) :
                new ClassPathLoader(rootClassLoader, files.toArray(new File[files.size()]));
        if (rootClassLoader == null) {
            rootClassLoader = (ClassPathLoader)ldr;
            ldr = new ClassPathLoader(rootClassLoader, files.toArray(new File[files.size()]));
            
        }
        ((ClassPathLoader)ldr).addExclude("org.cef.");
        final ClassLoader fLdr = ldr;
        Class c = Class.forName("com.codename1.impl.javase.Executor", true, ldr);
        Method m = c.getDeclaredMethod("main", String[].class);
        m.invoke(null, new Object[]{argv});

        new Thread() {
            public void run() {
                setContextClassLoader(fLdr);
                while (true) {
                    try {
                        sleep(500);
                    } catch (InterruptedException ex) {
                    }
                    String r = System.getProperty("reload.simulator");
                    if (r != null && r.equals("true")) {
                        System.out.println("Detected reload of simulator");
                        System.setProperty("reload.simulator", "");
                        try {
                            main(argv);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        return;
                    }
                }
            }
        }.start();
    }

    private static void addToSystemClassLoader(File f) {
        ClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;
        try {
            Method method = sysclass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{f.toURI().toURL()});
        } catch (Throwable t) {
            t.printStackTrace();
        }//end try catch    
    }
    
    static void loadFXRuntime() {
        String javahome = System.getProperty("java.home");
        String fx = javahome + "/lib/jfxrt.jar";
        File f = new File(fx);
        if (f.exists()) {
            addToSystemClassLoader(f);

        } 
    }
}
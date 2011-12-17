/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.cuba.security.entity.ui;

/**
 * <p>$Id$</p>
 *
 * @author artamonov
 */
public interface AssignableTarget {
    boolean isAssigned();

    String getPermissionValue();
}
/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

/*
 * This source code file constitutes a derivative work of Apache
 * Log4j v1.2.14 org.apache.log4j.Level and org.apache.log4j.Priority,
 * which is distributed under the following license.
 */

/*
 * Copyright 1999-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mitre.mpf.mvc.util.tailer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 Defines the minimum set of levels recognized by the system, that is
 <code>OFF</code>, <code>FATAL</code>, <code>ERROR</code>,
 <code>WARN</code>, <code>INFO</code, <code>DEBUG</code> and
 <code>ALL</code>.

 <p>The <code>MpfLogLevel</code> class may be subclassed to define a larger
 level set.
 */
public class MpfLogLevel implements Serializable {

    transient int level;
    transient String levelStr;
    transient int syslogEquivalent;

    public final static int OFF_INT = Integer.MAX_VALUE;
    public final static int FATAL_INT = 50000;
    public final static int ERROR_INT = 40000;
    public final static int WARN_INT  = 30000;
    public final static int INFO_INT  = 20000;
    public final static int DEBUG_INT = 10000;
    public final static int TRACE_INT = 5000;
    public final static int ALL_INT = Integer.MIN_VALUE;

    /**
     The <code>OFF</code> has the highest possible rank and is
     intended to turn off logging.  */
    final static public MpfLogLevel OFF = new MpfLogLevel(OFF_INT, "OFF", 0);

    /**
     The <code>FATAL</code> level designates very severe error
     events that will presumably lead the application to abort.
     */
    final static public MpfLogLevel FATAL = new MpfLogLevel(FATAL_INT, "FATAL", 0);

    /**
     The <code>ERROR</code> level designates error events that
     might still allow the application to continue running.  */
    final static public MpfLogLevel ERROR = new MpfLogLevel(ERROR_INT, "ERROR", 3);

    /**
     The <code>WARN</code> level designates potentially harmful situations.
     */
    final static public MpfLogLevel WARN  = new MpfLogLevel(WARN_INT, "WARN",  4);

    /**
     The <code>INFO</code> level designates informational messages
     that highlight the progress of the application at coarse-grained
     level.  */
    final static public MpfLogLevel INFO  = new MpfLogLevel(INFO_INT, "INFO",  6);

    /**
     The <code>DEBUG</code> level designates fine-grained
     informational events that are most useful to debug an
     application.  */
    final static public MpfLogLevel DEBUG = new MpfLogLevel(DEBUG_INT, "DEBUG", 7);

    /**
     * The <code>TRACE</code> level designates finer-grained
     * informational events than the <code>DEBUG</code level.
     *  @since 1.2.12
     */
    public static final MpfLogLevel TRACE = new MpfLogLevel(TRACE_INT, "TRACE", 7);


    /**
     The <code>ALL</code> has the lowest possible rank and is intended to
     turn on all logging.  */
    final static public MpfLogLevel ALL = new MpfLogLevel(ALL_INT, "ALL", 7);

    /**
     * Serialization version id.
     */
    static final long serialVersionUID = 3491141966387921974L;

    /**
     Instantiate a level object.
     */
    protected
    MpfLogLevel(int level, String levelStr, int syslogEquivalent) {
        this.level = level;
        this.levelStr = levelStr;
        this.syslogEquivalent = syslogEquivalent;
    }

    /**
     Convert the string passed as argument to a level. If the
     conversion fails, then this method returns {@link #DEBUG}.
     */
    public
    static
    MpfLogLevel toLevel(String sArg) {
        return toLevel(sArg, MpfLogLevel.DEBUG);
    }

    /**
     Convert an integer passed as argument to a level. If the
     conversion fails, then this method returns {@link #DEBUG}.
     */
    public
    static
    MpfLogLevel toLevel(int val) {
        return toLevel(val, MpfLogLevel.DEBUG);
    }

    /**
     Convert an integer passed as argument to a level. If the
     conversion fails, then this method returns the specified default.
     */
    public
    static
    MpfLogLevel toLevel(int val, MpfLogLevel defaultLevel) {
        switch(val) {
            case ALL_INT: return ALL;
            case DEBUG_INT: return MpfLogLevel.DEBUG;
            case INFO_INT: return MpfLogLevel.INFO;
            case WARN_INT: return MpfLogLevel.WARN;
            case ERROR_INT: return MpfLogLevel.ERROR;
            case FATAL_INT: return MpfLogLevel.FATAL;
            case TRACE_INT: return MpfLogLevel.TRACE;
            case OFF_INT: return OFF;
            default: return defaultLevel;
        }
    }

    /**
     Convert the string passed as argument to a level. If the
     conversion fails, then this method returns the value of
     <code>defaultLevel</code>.
     */
    public
    static
    MpfLogLevel toLevel(String sArg, MpfLogLevel defaultLevel) {
        if(sArg == null)
            return defaultLevel;

        String s = sArg.toUpperCase();

        if(s.equals("ALL")) return MpfLogLevel.ALL;
        if(s.equals("DEBUG")) return MpfLogLevel.DEBUG;
        if(s.equals("INFO"))  return MpfLogLevel.INFO;
        if(s.equals("WARN"))  return MpfLogLevel.WARN;
        if(s.equals("ERROR")) return MpfLogLevel.ERROR;
        if(s.equals("FATAL")) return MpfLogLevel.FATAL;
        if(s.equals("TRACE")) return MpfLogLevel.TRACE;
        if(s.equals("OFF")) return MpfLogLevel.OFF;
        return defaultLevel;
    }

    /**
     Returns <code>true</code> if this level has a higher or equal
     level than the level passed as argument, <code>false</code>
     otherwise.

     <p>You should think twice before overriding the default
     implementation of <code>isGreaterOrEqual</code> method.
     */
    public
    boolean isGreaterOrEqual(MpfLogLevel r) {
        return level >= r.level;
    }

    /**
     Returns the string representation of this priority.
     */
    final
    public
    String toString() {
        return levelStr;
    }

    /**
     Returns the integer representation of this level.
     */
    public
    final
    int toInt() {
        return level;
    }

    /**
     * Custom deserialization of MpfLogLevel.
     * @param s serialization stream.
     * @throws IOException if IO exception.
     * @throws ClassNotFoundException if class not found.
     */
    private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        level = s.readInt();
        syslogEquivalent = s.readInt();
        levelStr = s.readUTF();
    }

    /**
     * Serialize level.
     * @param s serialization stream.
     * @throws IOException if exception during serialization.
     */
    private void writeObject(final ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        s.writeInt(level);
        s.writeInt(syslogEquivalent);
        s.writeUTF(levelStr);
    }

    /**
     * Resolved deserialized level to one of the stock instances.
     * May be overriden in classes derived from MpfLogLevel.
     * @return resolved object.
     * @throws ObjectStreamException if exception during resolution.
     */
    private Object readResolve() throws ObjectStreamException {
        //
        //  if the deserizalized object is exactly an instance of MpfLogLevel
        //
        if (getClass() == MpfLogLevel.class) {
            return toLevel(level);
        }
        //
        //   extension of MpfLogLevel can't substitute stock item
        //
        return this;
    }

}

/*
 * PriorityThreadFactory
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger();
    private final String namePrefix;
    private final int threadPriority;

    public PriorityThreadFactory(int priority, Object className) {
        this(priority, className.getClass().getSimpleName());
    }

    public PriorityThreadFactory(int priority, String namePrefix) {
        this.namePrefix = namePrefix;
        this.threadPriority = priority;
    }

    public PriorityThreadFactory(String namePrefix) {
        this(Thread.NORM_PRIORITY, namePrefix);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
        t.setPriority(threadPriority);
        return t;
    }
}

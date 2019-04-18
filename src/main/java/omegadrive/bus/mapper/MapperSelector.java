/*
 * MapperSelector
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/04/19 14:33
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

package omegadrive.bus.mapper;

import omegadrive.SystemLoader;
import omegadrive.util.MsxXmlLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapperSelector {

    public static final Entry MISSING_DATA = new Entry();

    public static class Entry {
        public String title;
        public String mapperName;
        public String sha1;

        @Override
        public String toString() {
            return "Entry{" +
                    "title='" + title + '\'' +
                    ", mapperName='" + mapperName + '\'' +
                    ", sha1='" + sha1 + '\'' +
                    '}';
        }
    }

    static Map<SystemLoader.SystemType, Map<String, Entry>> cache = new HashMap<>();

    public static Entry getMapperData(SystemLoader.SystemType type, String sha1){
        if(!cache.containsKey(type)){
            switch (type){
                case MSX:
                    cache.put(type, MsxXmlLoader.loadData());
                    break;
                default:
                    cache.put(type, Collections.emptyMap());
                    break;
            }
        }
        return cache.get(type).getOrDefault(sha1, MISSING_DATA);
    }


}

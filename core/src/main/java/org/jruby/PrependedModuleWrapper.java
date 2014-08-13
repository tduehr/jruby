/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2014 Timur Duehr <tduehr@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.builtin.Variable;

// FIXME remove if still not needed for prepend (likely)

/**
 * This class is used as an intermediate superclass for Module#prepend
 *
 * @see org.jruby.IncludedModuleWrapper
 * @see org.jruby.RubyModule
 */
public class PrependedModuleWrapper extends IncludedModuleWrapper {

    public PrependedModuleWrapper(Ruby runtime, RubyClass superClass, RubyModule origin) {
        super(runtime, superClass, origin);
    }

    @Override
    public DynamicMethod searchMethodInner(String name) {
        DynamicMethod method = getMethods().get(name);
        if (name.equals("m1")) {
            System.out.println("searchMethodInner");
            System.out.println(method);
        }
        if (method != null) return method;
        if (name.equals("m1"))
            System.out.println(getSuperClass());
        // if (self == origin)
        //     return getSuperClass() == null ? null : getSuperClass().searchMethodInner(name);

        return origin.searchMethod(name);
    }

    @Override
    public List<IRubyObject> getPrependedAncestors() {
        ArrayList<IRubyObject> list = new ArrayList<IRubyObject>();

        System.out.println(getName());
        RubyModule topModule = origin.getMethodLocation();

        for (RubyModule module = origin.getSuperClass(); module != topModule; module = module.getSuperClass()) {
            if(module.isPrepended()) {
                list.addAll(module.getPrependedAncestors());
            }

            if(!module.isSingleton())
                list.add(module.getNonIncludedClass());
        }

        if(!topModule.isSingleton())
            list.add(topModule.getNonIncludedClass());

        return list;
    }

    @Override
    public boolean hasModuleInPrepends(RubyModule type) {
        RubyModule topModule = origin.getMethodLocation();

        for (RubyModule module = origin; module != this; module = module.getSuperClass()) {
            if (module.isSame(type) || module.hasModuleInPrepends(type))
                return true;
        }

        return false;
    }

    @Override
    public boolean hasPrepends() {
        return false;
    }

    @Override
    public boolean isPrepended() {
        return true;
    }
}

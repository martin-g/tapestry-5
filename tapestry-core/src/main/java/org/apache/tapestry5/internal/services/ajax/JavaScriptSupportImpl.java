// Copyright 2010 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.services.ajax;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tapestry5.Asset;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.FieldFocusPriority;
import org.apache.tapestry5.func.F;
import org.apache.tapestry5.func.Worker;
import org.apache.tapestry5.internal.InternalConstants;
import org.apache.tapestry5.internal.services.DocumentLinker;
import org.apache.tapestry5.internal.services.javascript.JavaScriptStackPathConstructor;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.ioc.services.Coercion;
import org.apache.tapestry5.ioc.util.IdAllocator;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.javascript.InitializationPriority;
import org.apache.tapestry5.services.javascript.JavaScriptStack;
import org.apache.tapestry5.services.javascript.JavaScriptStackSource;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;
import org.apache.tapestry5.services.javascript.StylesheetLink;

public class JavaScriptSupportImpl implements JavaScriptSupport
{
    private final IdAllocator idAllocator;

    private final DocumentLinker linker;

    // Using a Map as a case-insensitive set of stack names.

    private final Map<String, Boolean> addedStacks = CollectionFactory.newCaseInsensitiveMap();

    private final List<String> stackLibraries = CollectionFactory.newList();

    private final List<String> otherLibraries = CollectionFactory.newList();

    private final Set<String> importedStylesheetURLs = CollectionFactory.newSet();

    private final List<StylesheetLink> stylesheetLinks = CollectionFactory.newList();

    private final Map<InitializationPriority, JSONObject> inits = CollectionFactory.newMap();

    private final JavaScriptStackSource javascriptStackSource;

    private final JavaScriptStackPathConstructor stackPathConstructor;

    private FieldFocusPriority focusPriority;

    private String focusFieldId;

    private static final Coercion<Asset, String> toPath = new Coercion<Asset, String>()
    {
        public String coerce(Asset input)
        {
            return input.toClientURL();
        }
    };

    public JavaScriptSupportImpl(DocumentLinker linker, JavaScriptStackSource javascriptStackSource,
            JavaScriptStackPathConstructor stackPathConstructor)
    {
        this(linker, javascriptStackSource, stackPathConstructor, new IdAllocator(), false);
    }

    public JavaScriptSupportImpl(DocumentLinker linker, JavaScriptStackSource javascriptStackSource,
            JavaScriptStackPathConstructor stackPathConstructor, IdAllocator idAllocator, boolean partialMode)
    {
        this.linker = linker;
        this.idAllocator = idAllocator;
        this.javascriptStackSource = javascriptStackSource;
        this.stackPathConstructor = stackPathConstructor;

        // In partial mode, assume that the infrastructure stack is already present
        // (from the original page render).

        if (partialMode)
            addedStacks.put(InternalConstants.CORE_STACK_NAME, true);
    }

    public void commit()
    {
        if (focusFieldId != null)
            addInitializerCall("activate", focusFieldId);

        F.flow(stylesheetLinks).each(new Worker<StylesheetLink>()
        {
            public void work(StylesheetLink value)
            {
                linker.addStylesheetLink(value);
            }
        });

        Worker<String> linkLibrary = new Worker<String>()
        {
            public void work(String value)
            {
                linker.addScriptLink(value);
            }
        };

        F.flow(stackLibraries).each(linkLibrary);
        F.flow(otherLibraries).each(linkLibrary);

        for (InitializationPriority p : InitializationPriority.values())
        {
            JSONObject init = inits.get(p);

            if (init != null)
                linker.setInitialization(p, init);
        }
    }

    public void addInitializerCall(InitializationPriority priority, String functionName, JSONObject parameter)
    {
        storeInitializerCall(priority, functionName, parameter);
    }

    private void storeInitializerCall(InitializationPriority priority, String functionName, Object parameter)
    {
        assert priority != null;
        assert parameter != null;
        assert InternalUtils.isNonBlank(functionName);
        addCoreStackIfNeeded();

        JSONObject init = inits.get(priority);

        if (init == null)
        {
            init = new JSONObject();
            inits.put(priority, init);
        }

        JSONArray invocations = init.has(functionName) ? init.getJSONArray(functionName) : null;

        if (invocations == null)
        {
            invocations = new JSONArray();
            init.put(functionName, invocations);
        }

        invocations.put(parameter);
    }

    public void addInitializerCall(String functionName, JSONObject parameter)
    {
        addInitializerCall(InitializationPriority.NORMAL, functionName, parameter);
    }

    public void addInitializerCall(InitializationPriority priority, String functionName, String parameter)
    {
        storeInitializerCall(priority, functionName, parameter);
    }

    public void addInitializerCall(String functionName, String parameter)
    {
        addInitializerCall(InitializationPriority.NORMAL, functionName, parameter);
    }

    public void addScript(InitializationPriority priority, String format, Object... arguments)
    {
        addCoreStackIfNeeded();
        assert priority != null;
        assert InternalUtils.isNonBlank(format);

        String newScript = arguments.length == 0 ? format : String.format(format, arguments);

        linker.addScript(priority, newScript);
    }

    public void addScript(String format, Object... arguments)
    {
        addScript(InitializationPriority.NORMAL, format, arguments);
    }

    public String allocateClientId(ComponentResources resources)
    {
        return allocateClientId(resources.getId());
    }

    public String allocateClientId(String id)
    {
        return idAllocator.allocateId(id);
    }

    public void importJavaScriptLibrary(Asset asset)
    {
        assert asset != null;
        importJavaScriptLibrary(asset.toClientURL());
    }

    public void importJavaScriptLibrary(String libraryURL)
    {
        addCoreStackIfNeeded();

        if (otherLibraries.contains(libraryURL))
            return;

        otherLibraries.add(libraryURL);
    }

    private void addCoreStackIfNeeded()
    {
        addAssetsFromStack(InternalConstants.CORE_STACK_NAME);
    }

    private void addAssetsFromStack(String stackName)
    {
        if (addedStacks.containsKey(stackName))
            return;

        JavaScriptStack stack = javascriptStackSource.getStack(stackName);

        for (String dependentStackname : stack.getStacks())
        {
            addAssetsFromStack(dependentStackname);
        }

        stackLibraries.addAll(stackPathConstructor.constructPathsForJavaScriptStack(stackName));

        stylesheetLinks.addAll(stack.getStylesheets());

        String initialization = stack.getInitialization();

        if (initialization != null)
            linker.addScript(InitializationPriority.IMMEDIATE, initialization);

        addedStacks.put(stackName, true);
    }

    public void importStylesheet(Asset stylesheet)
    {
        assert stylesheet != null;
        importStylesheet(new StylesheetLink(stylesheet));
    }

    public void importStylesheet(StylesheetLink stylesheetLink)
    {
        assert stylesheetLink != null;
        String stylesheetURL = stylesheetLink.getURL();

        if (importedStylesheetURLs.contains(stylesheetURL))
            return;

        importedStylesheetURLs.add(stylesheetURL);

        stylesheetLinks.add(stylesheetLink);
    }

    public void importStack(String stackName)
    {
        assert InternalUtils.isNonBlank(stackName);
        addCoreStackIfNeeded();

        addAssetsFromStack(stackName);
    }

    public void autofocus(FieldFocusPriority priority, String fieldId)
    {
        assert priority != null;
        assert InternalUtils.isNonBlank(fieldId);

        if (focusFieldId == null || priority.compareTo(focusPriority) > 0)
        {
            this.focusPriority = priority;
            focusFieldId = fieldId;
        }
    }

}

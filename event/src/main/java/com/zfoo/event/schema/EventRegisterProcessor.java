/*
 * Copyright (C) 2020 The zfoo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.zfoo.event.schema;

import com.zfoo.event.manager.EventBus;
import com.zfoo.event.model.anno.EventReceiver;
import com.zfoo.event.model.event.IEvent;
import com.zfoo.event.model.vo.EnhanceUtils;
import com.zfoo.event.model.vo.EventReceiverDefinition;
import com.zfoo.protocol.util.ReflectionUtils;
import com.zfoo.protocol.util.StringUtils;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * @author jaysunxiao
 * @version 3.0
 */
public class EventRegisterProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            var clazz = bean.getClass();
            var methods = ReflectionUtils.getMethodsByAnnoInPOJOClass(clazz, EventReceiver.class);
            for (var method : methods) {
                var paramClazzs = method.getParameterTypes();
                if (paramClazzs.length != 1) {
                    throw new IllegalArgumentException(StringUtils.format("[class:{}] [method:{}] must have one parameter!", bean.getClass().getName(), method.getName()));
                }
                if (!IEvent.class.isAssignableFrom(paramClazzs[0])) {
                    throw new IllegalArgumentException(StringUtils.format("[class:{}] [method:{}] must have one [IEvent] type parameter!", bean.getClass().getName(), method.getName()));
                }

                var eventClazz = (Class<? extends IEvent>) paramClazzs[0];
                var eventName = eventClazz.getCanonicalName();
                var methodName = method.getName();

                if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException(StringUtils.format("[class:{}] [method:{}] [event:{}] must use 'public' as modifier!", bean.getClass().getName(), methodName, eventName));
                }

                if (Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException(StringUtils.format("[class:{}] [method:{}] [event:{}] can not use 'static' as modifier!", bean.getClass().getName(), methodName, eventName));
                }

                var expectedMethodName = StringUtils.format("on{}", eventClazz.getSimpleName());
                if (!methodName.equals(expectedMethodName)) {
                    throw new IllegalArgumentException(StringUtils.format("[class:{}] [method:{}] [event:{}] expects '{}' as method name!"
                            , bean.getClass().getName(), methodName, eventName, expectedMethodName));
                }

                var receiverDefinition = new EventReceiverDefinition(bean, method, eventClazz);
                var enhanceReceiverDefinition = EnhanceUtils.createEventReceiver(receiverDefinition);
                EventBus.registerEventReceiver(eventClazz, enhanceReceiverDefinition);
            }
        } catch (NotFoundException | CannotCompileException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        return bean;
    }

}

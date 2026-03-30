/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Shard running the contract defaults over another shard.
 *
 * <p>Calls go to the wrapped shard unless answered by name; defaults the
 * wrapped shard inherits run their own body, so they are what is
 * tested.</p>
 *
 * @since 0.1.0
 */
final class Wrapped implements InvocationHandler {

    /**
     * The shard answering abstract calls.
     */
    private final Shard origin;

    /**
     * Answers replacing calls, by method name.
     */
    private final Map<String, Answer> answers;

    /**
     * Creates the handler.
     *
     * @param origin Shard answering abstract calls
     * @param answers Answers by method name
     */
    private Wrapped(final Shard origin, final Map<String, Answer> answers) {
        this.origin = origin;
        this.answers = answers;
    }

    @Override
    public Object invoke(
        final Object proxy,
        final Method method,
        final Object[] args
    ) throws Throwable {
        final Object result;
        final Method own = this.origin.getClass()
            .getMethod(method.getName(), method.getParameterTypes());
        if (this.answers.containsKey(method.getName())) {
            result = this.answers.get(method.getName()).answer(args);
        } else if (own.isDefault()) {
            result = InvocationHandler.invokeDefault(proxy, method, args);
        } else {
            try {
                result = method.invoke(this.origin, args);
            } catch (final InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
        return result;
    }

    /**
     * Wraps a shard.
     *
     * @param origin Shard answering abstract calls
     * @param answers Answers by method name
     * @return Shard running the defaults
     */
    static Shard of(final Shard origin, final Map<String, Answer> answers) {
        return (Shard) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {Shard.class},
            new Wrapped(origin, answers)
        );
    }

    /**
     * Replacement of one call.
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    interface Answer {

        /**
         * Answers a call.
         *
         * @param args Call arguments, null when none
         * @return Result
         */
        Object answer(Object... args);
    }
}

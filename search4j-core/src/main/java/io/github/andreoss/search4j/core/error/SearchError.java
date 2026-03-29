/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * Base of every unchecked exception thrown by the search engine.
 *
 * <p>Concrete failures are reported as subclasses. Instances carry a
 * human-readable message, optional context text, an error type name, and an
 * optional system error number.</p>
 *
 * @since 0.1.0
 */
public abstract class SearchError extends RuntimeException {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message describing the error.
     */
    private final String message;

    /**
     * Optional context text.
     */
    private final String context;

    /**
     * Optional system error number; zero means none.
     */
    private final int errno;

    /**
     * Creates an error from a message, context and a system error number.
     *
     * @param message Human-readable description of the failure
     * @param context Machine-oriented detail, empty when none
     * @param errno System error number, zero when none
     */
    protected SearchError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message);
        this.message = message;
        this.context = context;
        this.errno = errno;
    }

    /**
     * Name of the error type.
     *
     * @return Simple class name of the concrete error
     */
    public String getType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public final String getMessage() {
        return this.message;
    }

    /**
     * Machine-oriented detail, such as which remote source raised the error.
     *
     * @return Context, empty when absent
     */
    public String getContext() {
        return this.context;
    }

    /**
     * Text naming the system error number.
     *
     * @return Text {@code system error N}, empty when the number is zero
     */
    public String getSystemMessage() {
        final String result;
        if (this.errno == 0) {
            result = "";
        } else {
            result = String.format("system error %d", this.errno);
        }
        return result;
    }

    /**
     * System error number behind the failure.
     *
     * @return Number, zero when absent
     */
    public int getErrno() {
        return this.errno;
    }

    /**
     * Type, message and context in one line.
     *
     * @return Text {@code Type: message}, plus {@code (...) context} when set
     */
    public String getDescription() {
        final StringBuilder result = new StringBuilder(this.getType());
        result.append(": ").append(this.message);
        if (!this.context.isEmpty()) {
            result.append(" (...) ").append(this.context);
        }
        return result.toString();
    }

    @Override
    public final String toString() {
        return this.getDescription();
    }
}

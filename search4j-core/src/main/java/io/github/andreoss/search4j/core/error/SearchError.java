/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * Base of every checked exception thrown by the search engine.
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
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
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
     * Returns the error type name.
     *
     * @return The type name
     */
    public String getType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public final String getMessage() {
        return this.message;
    }

    /**
     * Returns the optional context text.
     *
     * @return The context, or an empty string when absent
     */
    public String getContext() {
        return this.context;
    }

    /**
     * Returns the system error string for the stored number.
     *
     * @return The system string, or an empty string when no number is set
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
     * Returns the stored system error number.
     *
     * @return The error number, or zero when absent
     */
    public int getErrno() {
        return this.errno;
    }

    /**
     * Returns a description of this error.
     *
     * @return The description
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

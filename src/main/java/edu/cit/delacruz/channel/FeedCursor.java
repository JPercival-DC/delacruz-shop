package edu.cit.delacruz.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One durable row holding how far we've read the Tiangge order feed.
 * Always id=1 - there's only ever one cursor for this app.
 */
@Entity
@Table(name = "channel_feed_cursor")
class FeedCursor {

    static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    private long nextCursor;

    protected FeedCursor() {
        // JPA
    }

    FeedCursor(long nextCursor) {
        this.id = SINGLETON_ID;
        this.nextCursor = nextCursor;
    }

    long getNextCursor() {
        return nextCursor;
    }

    void setNextCursor(long nextCursor) {
        this.nextCursor = nextCursor;
    }
}

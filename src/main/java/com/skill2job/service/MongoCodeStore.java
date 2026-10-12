package com.skill2job.service;

import com.skill2job.entity.CodeDoc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/** MongoDB implementation of {@link CodeStore}. */
@Component
public class MongoCodeStore implements CodeStore {

    private final MongoTemplate mongo;

    public MongoCodeStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean reserveAttempt(Class<? extends CodeDoc> type, String id, int maxAttempts, Instant now) {
        Query q = Query.query(Criteria.where("_id").is(id).and("used").is(false)
                .and("attempts").lt(maxAttempts).and("expiresAt").gt(Date.from(now)));
        return mongo.updateFirst(q, new Update().inc("attempts", 1), type).getModifiedCount() == 1;
    }

    @Override
    public boolean markUsed(Class<? extends CodeDoc> type, String id, Instant now) {
        Query q = Query.query(Criteria.where("_id").is(id).and("used").is(false).and("expiresAt").gt(Date.from(now)));
        return mongo.updateFirst(q, new Update().set("used", true), type).getModifiedCount() == 1;
    }
}

package org.example.jcstress;

import org.example.ConcurrentHashMap;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest
@Outcome(id = "1", expect = Expect.ACCEPTABLE, desc = "writer 1 won")
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "writer 2 won")
@State
public class PutPutStress {
    private final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();

    @Actor
    public void writer1() {
        map.put(1, 1);
    }

    @Actor
    public void writer2() {
        map.put(1, 2);
    }

    @Arbiter
    public void check(I_Result r) {
        r.r1 = map.get(1);
    }
}

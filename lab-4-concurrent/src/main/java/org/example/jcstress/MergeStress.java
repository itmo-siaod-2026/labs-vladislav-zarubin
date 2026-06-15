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
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "both increments observed")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "lost update")
@State
public class MergeStress {
    private final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();

    @Actor
    public void inc1() {
        map.merge(1, 1, Integer::sum);
    }

    @Actor
    public void inc2() {
        map.merge(1, 1, Integer::sum);
    }

    @Arbiter
    public void check(I_Result r) {
        r.r1 = map.get(1);
    }
}

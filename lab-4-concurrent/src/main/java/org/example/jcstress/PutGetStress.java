package org.example.jcstress;

import org.example.ConcurrentHashMap;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "get observed before put")
@Outcome(id = "1", expect = Expect.ACCEPTABLE, desc = "get observed after put")
@State
public class PutGetStress {
    private final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();

    @Actor
    public void putter() {
        map.put(1, 1);
    }

    @Actor
    public void getter(I_Result r) {
        Integer v = map.get(1);
        r.r1 = v == null ? 0 : v;
    }
}

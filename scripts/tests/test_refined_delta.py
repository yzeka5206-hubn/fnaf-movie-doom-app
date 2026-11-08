"""Guard PCH2 copy selection and both bsdiff4 control representations."""
from pathlib import Path
import importlib.util
import random
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'make-apk-repair-delta-refined.py'
spec = importlib.util.spec_from_file_location('delta', SCRIPT)
delta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delta)

def encode(n):
    v=abs(n) | ((1<<63) if n<0 else 0)
    return v.to_bytes(8,'little')

class DeltaGeneration(unittest.TestCase):
    def test_native_and_serialized_control_match_exact_copy_runs(self):
        old=random.Random(2026).randbytes(50_000)
        new=bytearray(old);new[1_000:1_006]=b'ABCDEF';new[20_000:20_004]=b'xyz!';new=bytes(new)
        control,differences,extra=delta.bsdiff4.core.diff(old,new)
        self.assertIsInstance(control,(list,tuple))
        original=list(delta.refined_matches(old,new,111,222,0))
        serialized=b''.join(encode(v) for triple in control for v in triple)
        with patch.object(delta.bsdiff4.core,'diff',return_value=(serialized,differences,extra)):
            legacy=list(delta.refined_matches(old,new,111,222,0))
        self.assertEqual(original,legacy)
        self.assertGreater(len(original),0)
        for np,op,n in original:
            self.assertEqual(new[np-222:np-222+n],old[op-111:op-111+n])

    def test_malformed_control_rejected(self):
        with patch.object(delta.bsdiff4.core,'diff',return_value=([(1,2)],b'',b'')):
            with self.assertRaisesRegex(ValueError,'control triples'):
                list(delta.refined_matches(b'old',b'new',0,0,0))
        with patch.object(delta.bsdiff4.core,'diff',return_value=(b'not-24-bytes',b'',b'')):
            with self.assertRaisesRegex(ValueError,'control block'):
                list(delta.refined_matches(b'old',b'new',0,0,0))

    def test_excess_copy_runs_fall_back_to_literals(self):
        runs=[(i*600,i*600,300+i%100) for i in range(20)]
        ops=delta.plan_operations(runs,12_000,max_operations=10)
        self.assertLessEqual(len(ops),10)
        self.assertEqual(sum(n for _,_,n in ops),12_000)
        self.assertTrue(any(kind=='C' for kind,_,_ in ops))
        self.assertTrue(any(kind=='L' for kind,_,_ in ops))

    def test_backward_or_overlapping_runs_rejected(self):
        with self.assertRaisesRegex(ValueError,'backward or overlapping'):
            delta.plan_operations([(10,500,20),(40,400,20)],100)
        with self.assertRaisesRegex(ValueError,'backward or overlapping'):
            delta.plan_operations([(10,10,20),(25,40,20)],100)

if __name__=='__main__': unittest.main()

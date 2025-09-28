import multiprocessing as mp
from multiprocessing import shared_memory
import numpy as np
import time, os


def producer(shm_name, shape, count, finished, cond, num_messages=10):
    shm = shared_memory.SharedMemory(name=shm_name)
    buf = np.ndarray(shape, dtype=np.int32, buffer=shm.buf)

    for i in range(num_messages):
        data = np.arange(100, dtype=np.int32) + i
        with cond:
            buf[:] = data
            count.value += 1
            cond.notify_all()
        time.sleep(0.5)

    with cond:
        finished.value = 1
        cond.notify_all()

    shm.close()


def consumer(shm_name, shape, count, finished, cond, cid=1):
    shm = shared_memory.SharedMemory(name=shm_name)
    buf = np.ndarray(shape, dtype=np.int32, buffer=shm.buf)

    last_seen = 0
    while True:
        with cond:
            while count.value == last_seen and not finished.value:
                cond.wait()
            if finished.value and count.value == last_seen:
                break
            data = buf.copy()
            last_seen = count.value
        data_mean = process_data(data)
        print(f"Consumer {cid} đọc message {last_seen}, mean={data_mean:.1f}")

    shm.close()


def process_data(data):
    print(f"Processing data with mean {data.mean()}")
    return data.mean()


def main():
    shape = (100,)
    shm = shared_memory.SharedMemory(create=True, size=np.zeros(shape, dtype=np.int32).nbytes)
    buf = np.ndarray(shape, dtype=np.int32, buffer=shm.buf)

    lock = mp.Lock()
    cond = mp.Condition(lock)
    # count là biến dùng chung, i = int
    count = mp.Value("i", 0)
    finished = mp.Value("i", 0)

    p = mp.Process(target=producer, args=(shm.name, shape, count, finished, cond))
    c1 = mp.Process(target=consumer, args=(shm.name, shape, count, finished, cond, 1))
    c2 = mp.Process(target=consumer, args=(shm.name, shape, count, finished, cond, 2))

    p.start()
    c1.start()
    c2.start()
    p.join()
    c1.join()
    c2.join()

    shm.close()
    shm.unlink()


if __name__ == "__main__":
    mp.set_start_method("spawn", force=True)
    main()


import multiprocessing as mp
import queue, time, random, threading

# Xử lý từng loại task
def process_task(task):
    t = task.get("type")
    data = task.get("data", [])
    if t == "compute":
        return {"type": t, "result": sum(x * x for x in data)}
    elif t == "transform":
        op = task.get("op", "square")
        if op == "double":
            return {"type": t, "result": [x * 2 for x in data]}
        elif op == "sqrt":
            return {"type": t, "result": [x**0.5 for x in data if x >= 0]}
        else:  # square
            return {"type": t, "result": [x * x for x in data]}
    elif t == "analyze":
        if not data:
            return {"type": t, "error": "empty"}
        return {
            "type": t,
            "count": len(data),
            "mean": sum(data) / len(data),
            "min": min(data),
            "max": max(data),
        }
    return {"type": "error", "msg": f"Unknown type {t}"}

# Worker process: nhận task và trả kết quả
def worker(wid, tq, rq, active, shutdown):
    with active.get_lock():
        active.value += 1
    processed = 0
    try:
        while not shutdown.value:
            try:
                task = tq.get(timeout=1)
            except queue.Empty:
                continue
            if task == "STOP":
                break
            start = time.time()
            res = process_task(task)
            res["worker"] = wid
            res["time"] = time.time() - start
            rq.put(res)  
            processed += 1
    finally:
        with active.get_lock():
            active.value -= 1
        print(f"Worker {wid} finished, tasks processed: {processed}")


# Quản lý task / kết quả
class TaskManager:
    def __init__(self, workers=4, qsize=5000):
        self.tq = mp.Queue(qsize or 0)
        self.rq = mp.Queue(qsize or 0)
        self.active = mp.Value("i", 0)
        self.shutdown = mp.Value("i", 0)
        self.workers = [
            mp.Process(target=worker, args=(i + 1, self.tq, self.rq,
                                            self.active, self.shutdown))
            for i in range(workers)
        ]

    def start(self):
        for p in self.workers:
            p.start()
        print(f"Started {len(self.workers)} workers")

    def submit(self, task):
        try:
            self.tq.put(task, timeout=3)
            return True
        except queue.Full:
            return False

    def stop(self):
        self.shutdown.value = 1
        for _ in self.workers:
            self.tq.put("STOP")
        for p in self.workers:
            p.join()
        print("All workers stopped")

    def stats(self):
        return {
            "active_workers": self.active.value,
            "task_queue": self.tq.qsize(),
            "result_queue": self.rq.qsize(),
        }

# Sinh task test
def generate_tasks(n=1000):
    kinds = ["compute", "transform", "analyze"]
    tasks = []
    for _ in range(n):
        t = random.choice(kinds)
        if t == "compute":
            tasks.append({"type": t, "data": [random.randint(1, 50) for _ in range(40)]})
        elif t == "transform":
            tasks.append({
                "type": t,
                "op": random.choice(["square", "double", "sqrt"]),
                "data": [random.randint(1, 50) for _ in range(40)],
            })
        else:
            tasks.append({"type": t, "data": [random.randint(1, 200) for _ in range(60)]})
    return tasks


# Chạy test hiệu suất
def run_test(workers=4, num_tasks=1000):
    mgr = TaskManager(workers=workers, qsize=5000)
    mgr.start()

    tasks = generate_tasks(num_tasks)
    results = []

    # Collector thread lấy kết quả liên tục
    def collector():
        while len(results) < len(tasks):
            try:
                r = mgr.rq.get(timeout=1)
                results.append(r)
            except queue.Empty:
                if mgr.shutdown.value:
                    break

    collector_thread = threading.Thread(target=collector, daemon=True)
    collector_thread.start()

    start = time.time()
    for t in tasks:
        while not mgr.submit(t):
            time.sleep(0.01)
    print("All tasks submitted")

    # Chờ collector thu đủ kết quả
    collector_thread.join()
    total = time.time() - start

    times = [r.get("time", 0) for r in results]
    if times:
        print(f"Avg time/task: {sum(times)/len(times)*1000:.2f} ms")
    print(f"Processed {len(results)} tasks in {total:.2f}s "
          f"({len(results)/total:.1f} tasks/s)")
    print("Stats:", mgr.stats())

    mgr.stop()

# Main
if __name__ == "__main__":
    mp.set_start_method("spawn", force=True)
    run_test(workers=6, num_tasks=30000)

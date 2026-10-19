#!/usr/bin/env python3
"""Mede o jar real: start -> porta aberta -> 1ª resposta -> 10 requisições; RSS; confere o corpo com o oráculo.
Uso: JAVA=<jdk25>/bin/java [WAIT=segundos] measure.py <jar> <csv> <expected.json> <rótulo> [java_opts...]
Logs em run_<rótulo>.log no diretório atual."""
import json, os, re, socket, subprocess, sys, time, urllib.request, statistics
jar, csv, expected, label, *opts = sys.argv[1:]
JAVA = os.environ.get('JAVA', 'java')            # use um JDK 25: JAVA=/caminho/jdk-25/bin/java
PORT = 18081
log = open(f'run_{label}.log', 'w')
t0 = time.perf_counter()
p = subprocess.Popen([JAVA, *opts, '-jar', jar, f'--server.port={PORT}', f'--app.movies.csv-location=file:{csv}'],
                     stdout=log, stderr=subprocess.STDOUT)
def rss_mb(): return int(subprocess.check_output(['ps', '-o', 'rss=', '-p', str(p.pid)]).split()[0]) / 1024
peak = 0
while True:                                   # até a porta aceitar conexão (carga termina antes disso)
    try:
        socket.create_connection(('localhost', PORT), timeout=0.2).close(); break
    except OSError:
        peak = max(peak, rss_mb()); time.sleep(0.02)
        if p.poll() is not None: sys.exit('processo morreu; ver ' + f'run_{label}.log')
t_port = time.perf_counter() - t0
text = open(f'run_{label}.log').read()
assert 'filmes carregados' in text, 'MEDIÇÃO INVÁLIDA: log de carga concluída ainda não apareceu'
ts = lambda pat: re.search(r'(\d{2}):(\d{2}):(\d{2})\.(\d{3}).*' + pat, text)
def secs(m): h, mi, s, ms = map(int, m.groups()[:4]); return h*3600 + mi*60 + s + ms/1000
load_line = re.search(r'(\d+) filmes carregados', text).group(0)
t_start, t_loaded = secs(ts('Starting MoviesApplication')), secs(ts('filmes carregados'))
rss_after_load = rss_mb()
time.sleep(float(os.environ.get('WAIT', 0)))   # espera opcional depois da porta abrir
url = f'http://localhost:{PORT}/producers/award-intervals'
want = json.load(open(expected)); rows = []
for i in range(1, 11):
    t = time.perf_counter()
    with urllib.request.urlopen(url) as r: body = r.read(); status = r.status
    dt = (time.perf_counter() - t) * 1000
    ok = json.loads(body) == want
    rows.append((i, dt, len(body), status, ok))
rss_after = rss_mb()
p.terminate(); p.wait(timeout=30)
print(f'## {label}  [{" ".join(opts) or "JVM defaults"}]')
print(f'carga: {load_line} em {t_loaded - t_start:.2f}s (do log: Starting -> filmes carregados)')
print(f'start -> porta aceitando conexão: {t_port:.2f}s | RSS pico durante boot/carga: {peak:.0f} MB | RSS após carga: {rss_after_load:.0f} MB')
for i, dt, size, st, ok in rows: print(f'  req {i:2}: {dt:8.1f} ms  {size/1e6:.2f} MB  HTTP {st}  corpo == oráculo: {ok}')
ms = [r[1] for r in rows]
print(f'req 1 (fria): {ms[0]:.1f} ms | req 2-10: mediana {statistics.median(ms[1:]):.1f} ms, min {min(ms[1:]):.1f}, max {max(ms[1:]):.1f}')
print(f'RSS após 10 requisições: {rss_after:.0f} MB | todos == oráculo: {all(r[4] for r in rows)}')

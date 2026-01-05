# Disaster Recovery

**A comprehensive guide to business continuity for production payment systems**

---

## Table of Contents

1. [Disaster Recovery Fundamentals](#disaster-recovery-fundamentals)
2. [Backup Strategies](#backup-strategies)
3. [High Availability](#high-availability)
4. [Failover Mechanisms](#failover-mechanisms)
5. [Data Replication](#data-replication)
6. [Recovery Procedures](#recovery-procedures)
7. [Testing DR Plans](#testing-dr-plans)

---

## Disaster Recovery Fundamentals

### Key Metrics

**RTO (Recovery Time Objective):**
- How long can the system be down?
- Payment system: RTO = **15 minutes**

**RPO (Recovery Point Objective):**
- How much data can we lose?
- Payment system: RPO = **0 minutes** (zero data loss)

```
Disaster occurs        System back online
     │                        │
     ▼                        ▼
─────●────────────────────────●─────→ Time
     │◄───────RTO─────────────►│
     
     
Last backup      Data loss window
     │                   │
     ▼                   ▼
─────●───────────────────●─────────→ Time
     │◄──────RPO────────►│
```

### Disaster Scenarios

**1. Hardware Failure:**
- Server crash, disk failure
- **Impact**: Single node down
- **Recovery**: Automatic failover to replica

**2. Data Center Outage:**
- Power failure, network outage, natural disaster
- **Impact**: Entire region down
- **Recovery**: Failover to secondary region

**3. Data Corruption:**
- Software bug, malicious attack
- **Impact**: Database corrupted
- **Recovery**: Restore from backup

**4. Human Error:**
- Accidental deletion, bad deployment
- **Impact**: Application/data issues
- **Recovery**: Rollback deployment, restore data

---

## Backup Strategies

### Backup Types

**1. Full Backup:**
```sql
-- Complete database backup
pg_dump -U postgres -d payment_db -F c -f /backups/full_backup_2024-01-01.dump

-- Restore
pg_restore -U postgres -d payment_db /backups/full_backup_2024-01-01.dump
```

**Pros:** Complete data, simple to restore  
**Cons:** Large size, slow, high storage cost

**2. Incremental Backup:**
```sql
-- Backup only changes since last backup
pg_basebackup -D /backups/incremental_2024-01-01 -P -X stream

-- Restore requires: Last full backup + all incrementals
```

**Pros:** Fast, small size  
**Cons:** Slower restore (need all incrementals)

**3. Continuous Backup (WAL Archiving):**
```sql
-- postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /wal_archive/%f'

-- Backup all transaction logs continuously
```

**Pros:** Minimal data loss (RPO ~ seconds)  
**Cons:** Complex restore

### Automated Backup Schedule

```java
@Configuration
public class BackupConfiguration {
    
    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    public void dailyFullBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String backupFile = "/backups/payment_db_" + timestamp + ".dump";
        
        ProcessBuilder pb = new ProcessBuilder(
            "pg_dump",
            "-U", "postgres",
            "-d", "payment_db",
            "-F", "c",
            "-f", backupFile
        );
        
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                // Upload to S3
                s3Client.putObject("backups", backupFile, new File(backupFile));
                logger.info("Backup successful: {}", backupFile);
                
                // Delete local copy
                Files.delete(Paths.get(backupFile));
            } else {
                alertService.send("Backup failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            alertService.send("Backup error: " + e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 */15 * * * *")  // Every 15 minutes
    public void incrementalWALBackup() {
        // Archive WAL files to S3
        File walDir = new File("/var/lib/postgresql/wal_archive");
        File[] walFiles = walDir.listFiles();
        
        for (File walFile : walFiles) {
            s3Client.putObject("wal-backups", walFile.getName(), walFile);
            walFile.delete();  // Remove after upload
        }
    }
}
```

### Backup Retention Policy

```
Daily:  Keep 7 days
Weekly: Keep 4 weeks
Monthly: Keep 12 months
Yearly: Keep 7 years (compliance)
```

**Automated Cleanup:**

```java
@Scheduled(cron = "0 0 3 * * *")  // Daily at 3 AM
public void cleanupOldBackups() {
    LocalDate cutoffDaily = LocalDate.now().minusDays(7);
    LocalDate cutoffWeekly = LocalDate.now().minusWeeks(4);
    LocalDate cutoffMonthly = LocalDate.now().minusMonths(12);
    
    List<S3Object> backups = s3Client.listObjects("backups");
    
    for (S3Object backup : backups) {
        LocalDate backupDate = extractDate(backup.getKey());
        
        if (isDaily(backup) && backupDate.isBefore(cutoffDaily)) {
            s3Client.deleteObject("backups", backup.getKey());
        } else if (isWeekly(backup) && backupDate.isBefore(cutoffWeekly)) {
            s3Client.deleteObject("backups", backup.getKey());
        } else if (isMonthly(backup) && backupDate.isBefore(cutoffMonthly)) {
            s3Client.deleteObject("backups", backup.getKey());
        }
        // Keep yearly backups forever
    }
}
```

---

## High Availability

### Database Replication

**Setup:**

```
Primary (Master) Database
    ├──→ Synchronous Replica 1 (Same Region)
    ├──→ Asynchronous Replica 2 (Read Traffic)
    └──→ Asynchronous Replica 3 (DR - Different Region)
```

**PostgreSQL Configuration:**

```sql
-- Primary Server
-- postgresql.conf
wal_level = replica
max_wal_senders = 3
wal_keep_size = 1GB

-- pg_hba.conf
host replication replicator 10.0.0.0/8 md5

-- Create replication user
CREATE ROLE replicator WITH REPLICATION PASSWORD 'strong_password' LOGIN;

-- Replica Server
-- recovery.conf (PostgreSQL 12+: postgresql.conf)
primary_conninfo = 'host=primary-db port=5432 user=replicator password=strong_password'
primary_slot_name = 'replica_1'

-- For synchronous replication (zero data loss)
synchronous_commit = on
synchronous_standby_names = 'replica_1'
```

### Application-Level Routing

```java
@Configuration
public class DataSourceConfiguration {
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        
        // Primary (writes)
        DataSource primary = createDataSource("jdbc:postgresql://primary-db:5432/payment_db");
        targetDataSources.put("primary", primary);
        
        // Replicas (reads)
        DataSource replica1 = createDataSource("jdbc:postgresql://replica1-db:5432/payment_db");
        DataSource replica2 = createDataSource("jdbc:postgresql://replica2-db:5432/payment_db");
        targetDataSources.put("replica1", replica1);
        targetDataSources.put("replica2", replica2);
        
        RoutingDataSource router = new RoutingDataSource();
        router.setTargetDataSources(targetDataSources);
        router.setDefaultTargetDataSource(primary);
        
        return router;
    }
}

@Service
public class PaymentService {
    
    // Write operation → Primary
    @Transactional
    public void createPayment(PaymentRequest request) {
        DataSourceContext.set("primary");
        try {
            Payment payment = new Payment(request);
            paymentRepo.save(payment);
        } finally {
            DataSourceContext.clear();
        }
    }
    
    // Read operation → Replica (random selection)
    @Transactional(readOnly = true)
    public List<Payment> getUserPayments(Long userId) {
        String replica = selectReplica();  // Round-robin or least-load
        DataSourceContext.set(replica);
        try {
            return paymentRepo.findByUserId(userId);
        } finally {
            DataSourceContext.clear();
        }
    }
    
    private String selectReplica() {
        return (ThreadLocalRandom.current().nextInt(2) == 0) ? "replica1" : "replica2";
    }
}
```

### Load Balancer Configuration

```yaml
# NGINX
upstream payment_service {
    # Health check
    least_conn;  # Route to server with least connections
    
    server app1.payment.local:8080 max_fails=3 fail_timeout=30s;
    server app2.payment.local:8080 max_fails=3 fail_timeout=30s;
    server app3.payment.local:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    
    location /api/v1/payments {
        proxy_pass http://payment_service;
        
        # Timeouts
        proxy_connect_timeout 5s;
        proxy_send_timeout 10s;
        proxy_read_timeout 30s;
        
        # Health check
        proxy_next_upstream error timeout http_500 http_502 http_503;
        
        # Headers
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # Health check endpoint
    location /health {
        access_log off;
        proxy_pass http://payment_service/actuator/health;
    }
}
```

---

## Failover Mechanisms

### Automatic Failover

**Scenario:** Primary database fails

```
Before:
Client → Primary DB (Read/Write)
         Replica DB (Standby)

After Failover:
Client → Replica DB (Promoted to Primary)
         New Replica (Clone of new primary)
```

**Implementation with Patroni (PostgreSQL HA):**

```yaml
# patroni.yml
scope: payment_cluster
name: node1

restapi:
  listen: 0.0.0.0:8008
  connect_address: node1:8008

etcd:
  hosts: etcd1:2379,etcd2:2379,etcd3:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576
    
    postgresql:
      use_pg_rewind: true
      parameters:
        max_connections: 100
        wal_level: replica

postgresql:
  listen: 0.0.0.0:5432
  connect_address: node1:5432
  data_dir: /var/lib/postgresql/data
  
  authentication:
    replication:
      username: replicator
      password: strong_password
    superuser:
      username: postgres
      password: strong_password
```

**Automatic Promotion:**
```
1. Primary fails
2. Patroni detects failure (10s)
3. Elects new primary from replicas
4. Promotes replica to primary (5s)
5. Updates connection pool
6. Total downtime: ~15 seconds
```

### Application-Level Circuit Breaker

```java
@Service
public class DatabaseCircuitBreaker {
    private final CircuitBreaker circuitBreaker;
    
    public DatabaseCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)  // Open if 50% fail
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();
        
        this.circuitBreaker = CircuitBreaker.of("database", config);
    }
    
    public List<Payment> getPayments(Long userId) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                return paymentRepo.findByUserId(userId);
            } catch (DataAccessException e) {
                logger.error("Database access failed", e);
                // Fallback to cache
                return getCachedPayments(userId);
            }
        });
    }
}
```

---

## Data Replication

### Multi-Region Replication

```
US-East (Primary Region)
├── Primary DB
├── Replica 1
└── Replica 2
    │
    │ Async Replication
    ↓
EU-West (DR Region)
├── Replica 3 (Read-only)
└── Replica 4 (Read-only)
```

**Replication Lag Monitoring:**

```java
@Component
public class ReplicationMonitor {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void checkReplicationLag() {
        String sql = 
            "SELECT " +
            "  application_name, " +
            "  client_addr, " +
            "  state, " +
            "  EXTRACT(EPOCH FROM (NOW() - pg_last_xact_replay_timestamp())) as lag_seconds " +
            "FROM pg_stat_replication";
        
        List<ReplicationStatus> statuses = jdbcTemplate.query(sql, (rs, rowNum) -> {
            return new ReplicationStatus(
                rs.getString("application_name"),
                rs.getString("client_addr"),
                rs.getString("state"),
                rs.getDouble("lag_seconds")
            );
        });
        
        for (ReplicationStatus status : statuses) {
            if (status.getLagSeconds() > 60) {  // More than 1 minute lag
                alertService.send(String.format(
                    "High replication lag: %s (%s) - %.2f seconds",
                    status.getApplicationName(),
                    status.getClientAddr(),
                    status.getLagSeconds()
                ));
            }
            
            // Record metric
            metricsService.recordGauge(
                "replication_lag_seconds",
                status.getLagSeconds(),
                "replica", status.getApplicationName()
            );
        }
    }
}
```

### Kafka Multi-Region Replication

```yaml
# MirrorMaker 2 Configuration
clusters:
  - name: us-east
    bootstrap.servers: kafka-us-east:9092
  
  - name: eu-west
    bootstrap.servers: kafka-eu-west:9092

mirrors:
  - source: us-east
    target: eu-west
    topics:
      - payment-events
      - ledger-events
    
    # Replication settings
    sync.topic.configs.enabled: true
    replication.factor: 3
    
    # Consumer group sync
    sync.group.offsets.enabled: true
```

---

## Recovery Procedures

### Recovery Scenario 1: Single Node Failure

**Detection:**
```
Load Balancer Health Check fails
    ↓
Remove node from pool
    ↓
Auto-scale: Launch new instance
    ↓
New instance joins pool
```

**Automated:**
```yaml
# Kubernetes HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 3
  maxReplicas: 10
  
  # Scale based on pod readiness
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### Recovery Scenario 2: Database Corruption

**Manual Recovery Procedure:**

```bash
#!/bin/bash
# disaster_recovery.sh

echo "Starting disaster recovery..."

# 1. Stop application servers
kubectl scale deployment payment-service --replicas=0

# 2. Backup current (corrupted) database
pg_dump -U postgres -d payment_db -F c -f /tmp/corrupted_$(date +%Y%m%d).dump

# 3. Download latest good backup from S3
aws s3 cp s3://backups/payment_db_2024-01-01.dump /tmp/backup.dump

# 4. Drop and recreate database
psql -U postgres -c "DROP DATABASE IF EXISTS payment_db"
psql -U postgres -c "CREATE DATABASE payment_db"

# 5. Restore from backup
pg_restore -U postgres -d payment_db /tmp/backup.dump

# 6. Apply WAL files (point-in-time recovery)
# Restore up to specific time to exclude corrupt transaction
recovery_target_time='2024-01-01 14:30:00'
echo "recovery_target_time = '$recovery_target_time'" >> /var/lib/postgresql/recovery.conf

# Start PostgreSQL in recovery mode
systemctl start postgresql

# Wait for recovery to complete
until psql -U postgres -c "SELECT pg_is_in_recovery()" | grep "f"; do
    sleep 5
done

# 7. Verify data integrity
psql -U postgres -d payment_db -c "SELECT COUNT(*) FROM payments"

# 8. Restart application servers
kubectl scale deployment payment-service --replicas=5

echo "Recovery complete!"
```

### Recovery Scenario 3: Region Failure

**Failover Runbook:**

```markdown
# Region Failover Procedure

## Pre-requisites
- [ ] DR region replica is up-to-date (lag < 5 minutes)
- [ ] All team members notified
- [ ] Incident ticket created

## Steps

### 1. Update DNS (5 minutes)
```bash
# Point traffic to DR region
aws route53 change-resource-record-sets \
  --hosted-zone-id Z123456 \
  --change-batch file://failover-dns.json
```

### 2. Promote DR Replica (2 minutes)
```bash
# SSH to DR region
ssh dr-db-server

# Promote replica to primary
sudo -u postgres /usr/bin/pg_ctl promote -D /var/lib/postgresql/data
```

### 3. Update Application Config (3 minutes)
```bash
# Update environment variables
kubectl set env deployment/payment-service \
  DB_URL=jdbc:postgresql://dr-db:5432/payment_db \
  --namespace=production

# Restart pods
kubectl rollout restart deployment/payment-service
```

### 4. Verification (5 minutes)
- [ ] Application health check passes
- [ ] Process test payment
- [ ] Check error rates in monitoring
- [ ] Verify database writes working

### 5. Communication
- [ ] Update status page
- [ ] Email customers
- [ ] Internal announcement

## Rollback Procedure
If failover unsuccessful:
1. Revert DNS changes
2. Restart services in primary region
3. Investigate root cause
```

---

## Testing DR Plans

### Regular DR Drills

**Monthly Test:**
```java
@SpringBootTest
public class DisasterRecoveryTest {
    
    @Test
    public void testDatabaseFailover() {
        // 1. Verify system healthy
        ResponseEntity<String> health = restTemplate.getForEntity(
            "https://api.payment.com/health",
            String.class
        );
        assertEquals(200, health.getStatusCodeValue());
        
        // 2. Simulate primary database failure
        dockerCompose.stop("primary-db");
        
        // 3. Wait for failover (should be < 30 seconds)
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                    "https://api.payment.com/health",
                    String.class
                );
                return response.getStatusCodeValue() == 200;
            } catch (Exception e) {
                return false;
            }
        });
        
        // 4. Verify data integrity
        Payment payment = createTestPayment();
        assertNotNull(payment.getId());
        
        // 5. Restart primary
        dockerCompose.start("primary-db");
        
        // 6. Wait for replication to catch up
        Thread.sleep(10000);
        
        // 7. Verify payment exists on primary
        Payment retrieved = paymentRepo.findById(payment.getId()).orElse(null);
        assertNotNull(retrieved);
    }
    
    @Test
    public void testBackupRestore() {
        // 1. Create test data
        Payment payment = createTestPayment();
        
        // 2. Trigger backup
        backupService.createBackup();
        
        // 3. Delete data
        paymentRepo.deleteAll();
        
        // 4. Restore from backup
        restoreService.restoreLatestBackup();
        
        // 5. Verify data restored
        Payment restored = paymentRepo.findById(payment.getId()).orElse(null);
        assertNotNull(restored);
        assertEquals(payment.getAmount(), restored.getAmount());
    }
}
```

### Chaos Engineering

```java
@Configuration
@Profile("chaos")
public class ChaosConfiguration {
    
    // Randomly kill pods
    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    public void chaosPod() {
        if (Math.random() < 0.1) {  // 10% chance
            List<Pod> pods = kubernetes.listPods("payment-service");
            Pod victim = pods.get(ThreadLocalRandom.current().nextInt(pods.size()));
            
            logger.warn("CHAOS: Killing pod {}", victim.getName());
            kubernetes.deletePod(victim.getName());
        }
    }
    
    // Inject latency
    @Bean
    public ChaosMonkey chaosMonkey() {
        return ChaosMonkey.builder()
            .withLatencyActive(true)
            .withLatencyRangeStart(1000)
            .withLatencyRangeEnd(5000)
            .withLevel(ChaosLevel.LOW)
            .build();
    }
}
```

---

## Summary

**Disaster Recovery:**
✅ Plan for the worst, hope for the best  
✅ Automate recovery procedures  
✅ Test regularly  
✅ Monitor replication lag

**Key Metrics:**
- **RTO**: 15 minutes (how long down)
- **RPO**: 0 minutes (data loss tolerance)

**Strategies:**
- **Backups**: Daily full + continuous WAL
- **Replication**: Sync + async replicas
- **Failover**: Automatic with Patroni/K8s
- **Testing**: Monthly DR drills

**Recovery Scenarios:**
1. **Node Failure** → Auto-scale
2. **Database Failure** → Automatic failover
3. **Corruption** → Restore from backup
4. **Region Failure** → Manual failover to DR

**Best Practices:**
- [ ] Automate everything
- [ ] Document runbooks
- [ ] Test failover monthly
- [ ] Monitor replication lag
- [ ] Keep backups in multiple regions
- [ ] Practice chaos engineering

**Next Steps:**
- Read [scaling-strategy.md](scaling-strategy.md) for handling growth
- Study our DR runbooks in `/docs/runbooks/`
- Practice failover procedures in staging

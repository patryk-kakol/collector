package org.pk.collector.repository;

import org.pk.collector.domain.SftpFileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SftpFileRecordRepository extends JpaRepository<SftpFileRecord, String> {}

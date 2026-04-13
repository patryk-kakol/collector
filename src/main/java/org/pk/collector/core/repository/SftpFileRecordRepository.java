package org.pk.collector.core.repository;

import org.pk.collector.core.model.SftpFileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SftpFileRecordRepository extends JpaRepository<SftpFileRecord, String> {}

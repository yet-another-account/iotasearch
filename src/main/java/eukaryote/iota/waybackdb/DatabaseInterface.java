package eukaryote.iota.waybackdb;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import jota.model.Transaction;

public interface DatabaseInterface {
	@SqlQuery("select * from transactions where hash = :hash limit 1;")
	Transaction getTransaction(@Bind("hash") String hash);

	@SqlQuery("select * from transactions where bundle_hash = :hash;")
	List<Transaction> getBundle(@Bind("hash") String hash);

	@SqlQuery("select * from transactions where address = :hash order by timestamp desc;")
	List<Transaction> getTransactionsByAddress(@Bind("hash") String address);
}

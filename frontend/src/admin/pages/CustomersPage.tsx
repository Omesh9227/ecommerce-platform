import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminUsers, type AccountStatus, type AdminUser } from '../../api/admin';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { dateTime } from '../../lib/format';
import { ConfirmButton, PageHead, Pill, Table, Tabs, useParam } from '../ui';

const STATUS_TONE: Record<AccountStatus, 'good' | 'warn' | 'bad' | 'plain'> = { ACTIVE: 'good', INACTIVE: 'plain', SUSPENDED: 'bad', LOCKED: 'warn' };

export function CustomersPage() {
  const [role, setRole] = useParam('role', 'CUSTOMER');
  const [status, setStatus] = useParam('status');
  const [page, setPage] = useParam('page', '0');
  const list = useQuery({
    queryKey: ['admin-users', role, status, page],
    queryFn: () => adminUsers.list(Number(page), role === 'ALL' ? undefined : role, status || undefined),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });
  return (
    <div>
      <PageHead title="Customers" sub={list.data ? `${list.data.totalElements} accounts` : undefined}>
        <select aria-label="Role" className="field w-auto py-1.5" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="CUSTOMER">Customers</option>
          <option value="ADMIN">Admins</option>
          <option value="ALL">Everyone</option>
        </select>
      </PageHead>
      <Tabs label="Account status" value={status} options={[['', 'Any status'], ['ACTIVE', 'Active'], ['SUSPENDED', 'Suspended'], ['LOCKED', 'Locked'], ['INACTIVE', 'Inactive']]} onChange={setStatus} />
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      {list.data && (
        <>
          <Table label="Accounts" head={['Name', 'Email', 'Mobile', 'Joined', 'Status', '']}>
            {list.data.content.map((u) => (
              <UserRow key={u.userId} user={u} />
            ))}
          </Table>
          {list.data.content.length === 0 && <p className="mt-4 text-ink-soft">No accounts here.</p>}
          <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />
        </>
      )}
    </div>
  );
}

function UserRow({ user }: { user: AdminUser }) {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const change = useMutation({
    mutationFn: (status: AccountStatus) => adminUsers.setStatus(user.userId, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  });
  const self = me?.userId === user.userId;
  return (
    <tr data-testid="customer-row">
      <td className="px-4 py-2.5 font-semibold">
        {user.firstName} {user.lastName}
        {user.role === 'ADMIN' && <span className="ml-2"><Pill>Admin</Pill></span>}
      </td>
      <td className="px-4 py-2.5">{user.email}</td>
      <td className="px-4 py-2.5">{user.phoneNumber}</td>
      <td className="px-4 py-2.5 text-ink-soft">{dateTime(user.createdAt)}</td>
      <td className="px-4 py-2.5">
        <Pill tone={STATUS_TONE[user.accountStatus]}>{user.accountStatus.charAt(0) + user.accountStatus.slice(1).toLowerCase()}</Pill>
      </td>
      <td className="px-4 py-2.5">
        <div className="flex flex-wrap items-center gap-3">
          <Link to={`/admin/orders?userId=${user.userId}`} className="btn-link text-sm">
            Orders
          </Link>
          {!self &&
            (user.accountStatus === 'ACTIVE' ? (
              <ConfirmButton
                label="Suspend"
                className="btn-link text-sm"
                question="They’ll be signed out and can’t sign in."
                confirmLabel="Suspend"
                busy={change.isPending}
                onConfirm={() => change.mutate('SUSPENDED')}
              />
            ) : (
              <button type="button" className="btn-link text-sm" disabled={change.isPending} onClick={() => change.mutate('ACTIVE')}>
                Reactivate
              </button>
            ))}
        </div>
        {change.isError && <ErrorNote error={change.error} />}
      </td>
    </tr>
  );
}

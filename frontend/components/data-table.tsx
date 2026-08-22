"use client";

import * as React from "react";
import {
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconPlus,
  IconSearch,
  IconTrash,
  IconLink,
  IconDoorExit,
  IconCheck,
  IconHash,
  IconFileExport,
} from "@tabler/icons-react";
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
} from "@tanstack/react-table";
import Link from "next/link";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Checkbox } from "@/components/shadcn/checkbox";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/shadcn/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/shadcn/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/shadcn/tooltip";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/shadcn/alert-dialog";
import { SongDTO } from "@/hooks/models";
import { useGetPlaylist } from "@/hooks/generated/playlist-management/playlist-management";

interface SongsDataTableProps {
  data: SongDTO[];
  playlistId: number;
  inviteCode: string;
  onDelete?: (id: number) => void;
  onLeave?: () => void;
  isLoading?: boolean;
}

export function DataTable({
  data = [],
  playlistId,
  inviteCode,
  onDelete,
  onLeave,
}: SongsDataTableProps) {
  const handleExport = async () => {
    for (const type of ["info", "qr"] as const) {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/playlists/${playlistId}/export/${type}`,
        { credentials: "include" },
      );
      if (!response.ok) {
        toast.error(`Couldn't export the ${type} cards. Try again.`);
        continue;
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `playlist-${playlistId}-${type}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    }
  };

  const { data: playlist, isLoading } = useGetPlaylist(playlistId);

  const [rowSelection, setRowSelection] = React.useState({});
  const [columnFilters, setColumnFilters] = React.useState<ColumnFiltersState>(
    [],
  );
  const [pagination, setPagination] = React.useState({
    pageIndex: 0,
    pageSize: 10,
  });
  const [copiedLink, setLinkCopied] = React.useState(false);
  const [copiedCode, setCodeCopied] = React.useState(false);

  const handleCopyLink = async () => {
    const inviteLink = `${window.location.origin}/playlists/join/${inviteCode}`;
    await navigator.clipboard.writeText(inviteLink);
    setLinkCopied(true);
    setTimeout(() => setLinkCopied(false), 2000);
  };

  const handleCopyCode = async () => {
    await navigator.clipboard.writeText(inviteCode);
    setCodeCopied(true);
    setTimeout(() => setCodeCopied(false), 2000);
  };

  const columns: ColumnDef<SongDTO>[] = [
    {
      id: "select",
      header: ({ table }) => (
        <div className="flex items-center justify-center">
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected() ||
              (table.getIsSomePageRowsSelected() && "indeterminate")
            }
            onCheckedChange={(value) =>
              table.toggleAllPageRowsSelected(!!value)
            }
            aria-label="Select all"
          />
        </div>
      ),
      cell: ({ row }) => (
        <div className="flex items-center justify-center">
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={(value) => row.toggleSelected(!!value)}
            aria-label="Select row"
          />
        </div>
      ),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: "title",
      header: "Title",
      cell: ({ row }) => (
        <Link
          href={`/playlists/${playlistId}/songs/${row.original.id}`}
          className="text-foreground hover:underline cursor-pointer font-medium"
        >
          {row.original.title}
        </Link>
      ),
    },
    {
      accessorKey: "artist",
      header: "Artist",
      cell: ({ row }) => <div>{row.original.artist}</div>,
    },
    {
      accessorKey: "releaseYear",
      header: "Release Year",
      cell: ({ row }) => <div className="w-24">{row.original.releaseYear}</div>,
    },
    {
      accessorKey: "addedBy",
      header: "Added By",
      cell: ({ row }) => (
        <div>{row.original.addedBy?.username ?? "Unknown"}</div>
      ),
    },
    {
      id: "actions",
      cell: ({ row }) => (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="text-destructive hover:text-destructive hover:bg-destructive/10"
            >
              <IconTrash className="size-4" />
              <span className="sr-only">Delete song</span>
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete song?</AlertDialogTitle>
              <AlertDialogDescription>
                This will permanently remove &quot;{row.original.title}&quot;
                from the playlist.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                onClick={() => onDelete?.(row.original.id)}
              >
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ),
    },
  ];

  const table = useReactTable({
    data,
    columns,
    state: {
      rowSelection,
      columnFilters,
      pagination,
    },
    getRowId: (row) => row.id.toString(),
    enableRowSelection: true,
    onRowSelectionChange: setRowSelection,
    onColumnFiltersChange: setColumnFilters,
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  });

  if (isLoading) {
    return (
      <div className="w-full flex flex-col gap-6">
        <div className="h-96 flex items-center justify-center">
          <div className="text-muted-foreground">Loading playlist data...</div>
        </div>
      </div>
    );
  }

  return (
    <TooltipProvider>
      <div className="w-full flex flex-col gap-6">
        <div className="flex items-center justify-between gap-4 px-4 lg:px-6">
          <div className="relative flex-1 max-w-md">
            <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
            <Input
              placeholder="Search songs..."
              value={
                (table.getColumn("title")?.getFilterValue() as string) ?? ""
              }
              onChange={(event) =>
                table.getColumn("title")?.setFilterValue(event.target.value)
              }
              className="pl-9"
            />
          </div>

          <div className="flex items-center gap-2">
            <span>
              {playlist?.users.map((user) => user.username).join(", ")}
            </span>
            <Link href={`/playlists/${playlistId}/songs/add`}>
              <Button variant="outline" size="sm">
                <IconPlus />
                <span className="hidden lg:inline">Add Song</span>
              </Button>
            </Link>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="size-8"
                  onClick={handleCopyLink}
                >
                  {copiedLink ? (
                    <IconCheck className="size-4 text-green-500" />
                  ) : (
                    <IconLink className="size-4" />
                  )}
                  <span className="sr-only">Copy invite link</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                {copiedLink ? "Copied!" : "Copy invite link"}
              </TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="size-8"
                  onClick={handleCopyCode}
                >
                  {copiedCode ? (
                    <IconCheck className="size-4 text-green-500" />
                  ) : (
                    <IconHash className="size-4" />
                  )}
                  <span className="sr-only">Copy invite code</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                {copiedCode ? "Copied!" : "Copy invite code"}
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="size-8"
                  onClick={handleExport}
                >
                  <IconFileExport className="size-4" />
                  <span className="sr-only">Export playlist</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>Export as PDF</TooltipContent>
            </Tooltip>

            <AlertDialog>
              <Tooltip>
                <TooltipTrigger asChild>
                  <AlertDialogTrigger asChild>
                    <Button
                      variant="outline"
                      size="icon"
                      className="size-8 text-destructive hover:text-destructive hover:bg-destructive/10 hover:border-destructive/30"
                    >
                      <IconDoorExit className="size-4" />
                      <span className="sr-only">Leave playlist</span>
                    </Button>
                  </AlertDialogTrigger>
                </TooltipTrigger>
                <TooltipContent>Leave playlist</TooltipContent>
              </Tooltip>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Leave playlist?</AlertDialogTitle>
                  <AlertDialogDescription>
                    You&apos;ll lose access to this playlist. You can rejoin
                    later with an invite link.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    onClick={onLeave}
                  >
                    Leave
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>

        <div className="flex flex-col gap-4 px-4 lg:px-6">
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader className="bg-muted sticky top-0 z-10">
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id}>
                        {header.isPlaceholder
                          ? null
                          : flexRender(
                              header.column.columnDef.header,
                              header.getContext(),
                            )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows?.length ? (
                  table.getRowModel().rows.map((row) => (
                    <TableRow
                      key={row.id}
                      data-state={row.getIsSelected() && "selected"}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id}>
                          {flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext(),
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell
                      colSpan={columns.length}
                      className="h-24 text-center"
                    >
                      {data.length === 0
                        ? "No songs in this playlist yet."
                        : "No songs match your search."}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-between px-4">
            <div className="text-muted-foreground hidden flex-1 text-sm lg:flex">
              {table.getFilteredSelectedRowModel().rows.length} of{" "}
              {table.getFilteredRowModel().rows.length} song(s) selected.
            </div>
            <div className="flex w-full items-center gap-8 lg:w-fit">
              <div className="hidden items-center gap-2 lg:flex">
                <Label htmlFor="rows-per-page" className="text-sm font-medium">
                  Rows per page
                </Label>
                <Select
                  value={`${table.getState().pagination.pageSize}`}
                  onValueChange={(value) => {
                    table.setPageSize(Number(value));
                  }}
                >
                  <SelectTrigger size="sm" className="w-20" id="rows-per-page">
                    <SelectValue
                      placeholder={table.getState().pagination.pageSize}
                    />
                  </SelectTrigger>
                  <SelectContent side="top">
                    {[10, 20, 30, 40, 50].map((pageSize) => (
                      <SelectItem key={pageSize} value={`${pageSize}`}>
                        {pageSize}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex w-fit items-center justify-center text-sm font-medium">
                Page {table.getState().pagination.pageIndex + 1} of{" "}
                {table.getPageCount()}
              </div>
              <div className="ml-auto flex items-center gap-2 lg:ml-0">
                <Button
                  variant="outline"
                  className="hidden h-8 w-8 p-0 lg:flex"
                  onClick={() => table.setPageIndex(0)}
                  disabled={!table.getCanPreviousPage()}
                >
                  <span className="sr-only">Go to first page</span>
                  <IconChevronsLeft />
                </Button>
                <Button
                  variant="outline"
                  className="size-8"
                  size="icon"
                  onClick={() => table.previousPage()}
                  disabled={!table.getCanPreviousPage()}
                >
                  <span className="sr-only">Go to previous page</span>
                  <IconChevronLeft />
                </Button>
                <Button
                  variant="outline"
                  className="size-8"
                  size="icon"
                  onClick={() => table.nextPage()}
                  disabled={!table.getCanNextPage()}
                >
                  <span className="sr-only">Go to next page</span>
                  <IconChevronRight />
                </Button>
                <Button
                  variant="outline"
                  className="hidden size-8 lg:flex"
                  size="icon"
                  onClick={() => table.setPageIndex(table.getPageCount() - 1)}
                  disabled={!table.getCanNextPage()}
                >
                  <span className="sr-only">Go to last page</span>
                  <IconChevronsRight />
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </TooltipProvider>
  );
}
